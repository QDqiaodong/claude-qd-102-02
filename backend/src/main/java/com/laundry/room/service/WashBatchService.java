package com.laundry.room.service;

import com.laundry.room.dto.BatchDonePayload;
import com.laundry.room.dto.BizException;
import com.laundry.room.entity.Linen;
import com.laundry.room.entity.WashBatch;
import com.laundry.room.entity.WashShortage;
import com.laundry.room.repository.LinenRepository;
import com.laundry.room.repository.WashBatchRepository;
import com.laundry.room.repository.WashShortageRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WashBatchService {

    private final WashBatchRepository batches;
    private final LinenRepository linens;
    private final WashShortageRepository shortages;

    public WashBatchService(WashBatchRepository batches, LinenRepository linens,
                            WashShortageRepository shortages) {
        this.batches = batches;
        this.linens = linens;
        this.shortages = shortages;
    }

    public List<WashBatch> list(Long linenId, String status) {
        return batches.findAllByOrderByIdDesc().stream()
                .filter(b -> linenId == null || linenId.equals(b.linenId))
                .filter(b -> status == null || status.isEmpty() || status.equals(b.status))
                .toList();
    }

    @Transactional
    public WashBatch create(WashBatch input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        if (batches.existsByCode(input.code)) {
            throw new BizException("批次号 " + input.code + " 已经用过了");
        }
        if (input.linenId == null) {
            throw new BizException("请选择要送洗的布草");
        }
        if (input.quantity == null || input.quantity <= 0) {
            throw new BizException("送洗件数要大于 0");
        }
        if (input.sendDate == null || input.expectDate == null) {
            throw new BizException("送洗日期和预计回洗日期都要填");
        }
        if (input.expectDate.isBefore(input.sendDate)) {
            throw new BizException("预计回洗日期不能早于送洗日期");
        }
        // 先锁布草台账行：送洗即出库，两个人同时开批在这里排队。
        Linen linen = linens.findByIdForUpdate(input.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        if (ContaminatedSealService.SPECIAL_WASH.equals(input.washType)) {
            throw new BizException("专洗只能从污染封存单挂批，不能在这里开");
        }
        if ("停用".equals(linen.status)) {
            throw new BizException("布草 " + linen.name + " 已经停用了，不能送洗");
        }
        if (input.quantity > linen.stock) {
            throw new BizException("在库只有 " + linen.stock + " 件，送洗不了 " + input.quantity + " 件");
        }
        // 同一件布草同一天只开一个常规批次；专洗由封存单挂批，不受这条限制。
        boolean sameDayRegular = batches.findByLinenIdAndSendDate(linen.id, input.sendDate).stream()
                .anyMatch(b -> !ContaminatedSealService.SPECIAL_WASH.equals(b.washType));
        if (sameDayRegular) {
            throw new BizException("这件布草 " + input.sendDate + " 已经开过批次了，同一天不用开两批");
        }
        WashBatch saved = new WashBatch();
        saved.code = input.code.trim();
        saved.linenId = linen.id;
        saved.quantity = input.quantity;
        saved.sendDate = input.sendDate;
        saved.expectDate = input.expectDate;
        saved.washType = (input.washType == null || input.washType.isBlank())
                ? "常规" : input.washType;
        saved.status = "待洗";
        saved.operator = input.operator;
        saved.sealId = null;
        saved.returnQty = null;
        saved = batches.save(saved);

        // 送洗即出库：这几件出门去洗，在库先扣掉；收工时才按实际回洗件数回库。
        // 追差没结案的短少件数，永远不会经过这一步回到在库。
        linen.stock = linen.stock - saved.quantity;
        linens.save(linen);
        return saved;
    }

    @Transactional
    public WashBatch advance(Long id, String action, BatchDonePayload payload) {
        WashBatch batch = batches.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("批次不存在"));
        if ("start".equals(action)) {
            if (!"待洗".equals(batch.status)) {
                throw new BizException("只有待洗的批次能开工，现在是 " + batch.status);
            }
            batch.status = "洗涤中";
        } else if ("finish".equals(action)) {
            if (!"洗涤中".equals(batch.status)) {
                throw new BizException("只有洗涤中的批次能送烘干，现在是 " + batch.status);
            }
            batch.status = "待烘干";
        } else if ("done".equals(action)) {
            return finish(batch, payload);
        } else {
            throw new BizException("不认识的动作：" + action);
        }
        return batches.save(batch);
    }

    /**
     * 收工：批次行已经在上游锁住，两个人同时点收工只成一个——
     * 后到的那个人拿到的报错会说清：是追差已经挂着，还是这张批次根本还没到已完成。
     *
     * 常规 / 强化批次按实际回洗件数回库；回洗少于送洗时，短少件数当场挂追差，
     * 追差没结案，这几件就不进可领用在库——在库只涨回洗回来的部分。
     */
    private WashBatch finish(WashBatch batch, BatchDonePayload payload) {
        if (!"待烘干".equals(batch.status)) {
            if ("已完成".equals(batch.status)) {
                List<WashShortage> open = shortages.findByBatchIdAndStatus(
                        batch.id, WashShortageService.OPEN);
                if (!open.isEmpty()) {
                    WashShortage s = open.get(0);
                    throw new BizException("这张批次已经收工，短少 " + s.shortQty + " 件的追差 "
                            + s.code + " 还挂着没结案，不能重复收工");
                }
                throw new BizException("这张批次已经收工了（已完成），不能重复收工");
            }
            throw new BizException("只有待烘干的批次能收工，这张批次现在是 " + batch.status
                    + "，还没到已完成");
        }
        if (ContaminatedSealService.SPECIAL_WASH.equals(batch.washType)) {
            // 专洗的件数由封存单管住：解除封存时才还库，这里不碰在库、不挂追差。
            if (payload != null && payload.returnQty != null
                    && !payload.returnQty.equals(batch.quantity)) {
                throw new BizException("专洗批次的件数由封存单管住，少了要走封存流程，这里不挂追差");
            }
            batch.returnQty = batch.quantity;
            batch.status = "已完成";
            return batches.save(batch);
        }

        int returnQty = (payload == null || payload.returnQty == null)
                ? batch.quantity : payload.returnQty;
        if (returnQty < 0 || returnQty > batch.quantity) {
            throw new BizException("回洗件数要在 0 到送洗件数 " + batch.quantity + " 之间");
        }
        int shortQty = batch.quantity - returnQty;
        if (shortQty > 0) {
            String duty = payload == null ? null : payload.duty;
            String founder = payload == null ? null : payload.founder;
            if (duty == null || duty.isBlank()) {
                throw new BizException("回洗少了 " + shortQty + " 件，必须当场填责任楼层或工序");
            }
            if (founder == null || founder.isBlank()) {
                throw new BizException("回洗少了 " + shortQty + " 件，必须当场填发现人");
            }
            // 同一事务里把追差单落下：短少件数、布草、责任楼层或工序、发现人，一样不能少。
            WashShortage s = new WashShortage();
            s.batchId = batch.id;
            s.linenId = batch.linenId;
            s.sendQty = batch.quantity;
            s.returnQty = returnQty;
            s.shortQty = shortQty;
            s.duty = duty.trim();
            s.founder = founder.trim();
            s.foundAt = LocalDateTime.now();
            s.status = WashShortageService.OPEN;
            // 先拿自增主键再回填 ZC-xxxx 单号，避免并发抢号撞单。
            s.code = "TMP-" + Long.toHexString(System.nanoTime());
            s = shortages.save(s);
            s.code = String.format("ZC-%04d", s.id);
            shortages.save(s);
        }

        Linen linen = linens.findByIdForUpdate(batch.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        // 停用和收工共用这把布草行锁，两人一边点停用一边点收工只成一件：
        // 停用先拿锁，这里看到的就是停用档案——件数不许加回可领用，收工整单回滚，
        // 批次仍是「待烘干」，等档案重新启用或盘点后再处理。
        if ("停用".equals(linen.status)) {
            throw new BizException("布草 " + linen.name + " 已经停用，这批回洗的 " + returnQty
                    + " 件不能加回可领用在库；先把档案启用再收工，或找库房盘点落定");
        }
        // 只按实际回洗件数回库；短少的几件扣在库外，等追差结案（补回 / 报损确认）。
        linen.stock = linen.stock + returnQty;
        linens.save(linen);

        batch.returnQty = returnQty;
        batch.status = "已完成";
        return batches.save(batch);
    }
}
