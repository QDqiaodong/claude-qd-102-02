package com.laundry.room.service;

import com.laundry.room.dto.BizException;
import com.laundry.room.entity.ContaminatedSeal;
import com.laundry.room.entity.FloorIssue;
import com.laundry.room.entity.Linen;
import com.laundry.room.entity.LinenLoss;
import com.laundry.room.entity.WashBatch;
import com.laundry.room.entity.WashShortage;
import com.laundry.room.repository.ContaminatedSealRepository;
import com.laundry.room.repository.FloorIssueRepository;
import com.laundry.room.repository.LinenLossRepository;
import com.laundry.room.repository.LinenRepository;
import com.laundry.room.repository.WashBatchRepository;
import com.laundry.room.repository.WashShortageRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinenService {

    private final LinenRepository linens;
    private final ContaminatedSealRepository seals;
    private final WashBatchRepository batches;
    private final FloorIssueRepository issues;
    private final LinenLossRepository losses;
    private final WashShortageRepository shortages;

    public LinenService(LinenRepository linens, ContaminatedSealRepository seals,
                        WashBatchRepository batches, FloorIssueRepository issues,
                        LinenLossRepository losses, WashShortageRepository shortages) {
        this.linens = linens;
        this.seals = seals;
        this.batches = batches;
        this.issues = issues;
        this.losses = losses;
        this.shortages = shortages;
    }

    public List<Linen> list(String category, String status, String keyword) {
        return linens.findAllByOrderByIdAsc().stream()
                .filter(l -> category == null || category.isEmpty() || category.equals(l.category))
                .filter(l -> status == null || status.isEmpty() || status.equals(l.status))
                .filter(l -> keyword == null || keyword.isEmpty()
                        || l.name.contains(keyword) || l.code.contains(keyword))
                .toList();
    }

    @Transactional
    public Linen create(Linen input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("布草编号不能为空");
        }
        if (linens.existsByCode(input.code)) {
            throw new BizException("编号 " + input.code + " 已经被别的布草用掉了");
        }
        if (input.stock != null && input.stock < 0) {
            throw new BizException("在库件数不能是负数");
        }
        Linen saved = new Linen();
        saved.code = input.code.trim();
        saved.name = input.name;
        saved.category = (input.category == null || input.category.isBlank())
                ? "床单" : input.category;
        saved.spec = input.spec;
        saved.stock = input.stock == null ? 0 : input.stock;
        saved.warnStock = (input.warnStock == null || input.warnStock <= 0) ? 20 : input.warnStock;
        saved.status = (input.status == null || input.status.isBlank()) ? "在用" : input.status;
        return linens.save(saved);
    }

    @Transactional
    public Linen update(Long id, Linen input) {
        // 先锁台账行：停用和收工、补回、解除封存抢同一把行锁，一边点停用一边点收工只成一个。
        Linen l = linens.findByIdForUpdate(id).orElseThrow(() -> new BizException("布草不存在"));
        if (input.name != null) {
            l.name = input.name;
        }
        if (input.category != null && !input.category.isBlank()) {
            l.category = input.category;
        }
        if (input.spec != null) {
            l.spec = input.spec;
        }
        if (input.warnStock != null && input.warnStock > 0
                && !input.warnStock.equals(l.warnStock)) {
            l.warnStock = input.warnStock;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(l.status)) {
            if ("停用".equals(input.status)) {
                assertNothingInFlight(l);
            }
            l.status = input.status;
        }
        return linens.save(l);
    }

    /**
     * 停用前的在途检查：台账行已经在上游锁住，在库为零只是第一关。
     * 送洗扣光之后的空窗里，批次还挂在待洗 / 洗涤中 / 待烘干，件数只是出门不是没了——
     * 这些单据没清掉就停用，收工、补回、解除封存又会把件数加回可领用，
     * 楼层口按停用拦住、库房台账却对不上。卡在哪一张单，报错就说哪一张。
     */
    private void assertNothingInFlight(Linen l) {
        if (l.stock != null && l.stock > 0) {
            throw new BizException("这件布草在库还有 " + l.stock + " 件，处理完才能停用");
        }
        List<ContaminatedSeal> activeSeals = seals.findByLinenIdAndStatus(
                l.id, ContaminatedSealService.ACTIVE);
        if (!activeSeals.isEmpty()) {
            throw new BizException("这件布草还有没解除的污染封存 " + activeSeals.get(0).code
                    + "，等专洗完解除封存再停用");
        }
        List<WashBatch> openBatches = batches.findByLinenIdAndStatusNot(l.id, "已完成");
        if (!openBatches.isEmpty()) {
            WashBatch b = openBatches.get(0);
            throw new BizException("洗涤批次 " + b.code + " 还没走到已完成（现在 " + b.status
                    + "），送洗的 " + b.quantity + " 件还在外面，不能停用；等批次收工再停");
        }
        List<FloorIssue> openIssues = issues.findByLinenIdAndStatus(l.id, "已送出");
        if (!openIssues.isEmpty()) {
            FloorIssue i = openIssues.get(0);
            throw new BizException(i.floorCode + " 在 " + i.issueDate + " 送出的 " + i.sendQty
                    + " 件还没收回，不能停用；先在楼层收发里登记收回再停");
        }
        List<LinenLoss> pendingLosses = losses.findByLinenIdAndStatus(l.id, "待确认");
        if (!pendingLosses.isEmpty()) {
            LinenLoss loss = pendingLosses.get(0);
            throw new BizException(loss.lossDate + " 登记的报损（" + loss.reason + " " + loss.quantity
                    + " 件）还待确认，不能停用；先到报损赔付里确认再停");
        }
        List<WashShortage> openShortages = shortages.findByLinenIdAndStatus(
                l.id, WashShortageService.OPEN);
        if (!openShortages.isEmpty()) {
            WashShortage s = openShortages.get(0);
            throw new BizException("回洗追差 " + s.code + " 还挂着没结案（短少 " + s.shortQty
                    + " 件），不能停用；先补回入库或转报损结案再停");
        }
    }
}
