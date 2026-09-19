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

    /** 批次洗到这一步才算库房收到货，之前都算还没收回的在途件。 */
    private static final String BATCH_DONE = "已完成";
    /** 报损确认后才算落定，待确认时件数归属还没说清。 */
    private static final String LOSS_PENDING = "待确认";

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
        // 改档案先拿布草台账行锁：收工 / 补回加库也要拿这把锁，
        // 两个人一个点停用、一个点收工在这里排队，保证只成一件事。
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
                ensureCanDeactivate(l);
            }
            l.status = input.status;
        }
        return linens.save(l);
    }

    /**
     * 停用前必须把这一件布草所有在外头的件、没说清归属的单都收回来。
     *
     * 只看「在库是不是零」挡不住送洗扣光之后的空窗：批次一开在库当场扣光，
     * 可待洗 / 洗涤中 / 待烘干的批次还挂着，楼层送出的、报损待定的也都可能在外头。
     * 所以在库归零只是第一关，四类在途单据逐一查清楚，每一关卡住都报明是哪一张单。
     */
    private void ensureCanDeactivate(Linen l) {
        if (l.stock != null && l.stock > 0) {
            throw new BizException("这件布草在库还有 " + l.stock + " 件，处理完才能停用");
        }
        List<ContaminatedSeal> activeSeals =
                seals.findByLinenIdAndStatus(l.id, ContaminatedSealService.ACTIVE);
        if (!activeSeals.isEmpty()) {
            ContaminatedSeal s = activeSeals.get(0);
            throw new BizException("污染封存 " + s.code + " 还没解除（还封着 " + s.remainQty
                    + " 件），等专洗完解除封存再停用");
        }
        // 没收到「已完成」的洗涤批次：待洗 / 洗涤中 / 待烘干都算件还在外头。
        // 专洗批次在这里也一样拦——封存未解除时上一关已经拦住，这一关再兜底。
        List<WashBatch> openBatches = batches.findByLinenIdAndStatusNot(l.id, BATCH_DONE);
        if (!openBatches.isEmpty()) {
            WashBatch b = openBatches.get(0);
            throw new BizException("洗涤批次 " + b.code + " 还在「" + b.status + "」、送洗的 "
                    + b.quantity + " 件还没洗回收到，等收工入库再停用");
        }
        // 已送出、没收回的楼层单：件还在楼层手里。
        List<FloorIssue> openIssues = issues.findByLinenIdAndStatus(l.id, "已送出");
        if (!openIssues.isEmpty()) {
            FloorIssue i = openIssues.get(0);
            throw new BizException("楼层单 #" + i.id + "（" + i.floorCode + " " + i.issueDate
                    + "）还挂着「已送出」、送出的 " + i.sendQty + " 件没收回，收完数再停用");
        }
        // 待确认的报损：确认时才真扣库，确认前这几件算不算在库还没说定。
        List<LinenLoss> pendingLosses = losses.findByLinenIdAndStatus(l.id, LOSS_PENDING);
        if (!pendingLosses.isEmpty()) {
            LinenLoss loss = pendingLosses.get(0);
            throw new BizException("报损单 #" + loss.id + "（" + loss.lossDate + "，"
                    + loss.quantity + " 件）还是「" + loss.status + "」没确认，确认完再停用");
        }
        // 未结案的回洗追差：短少件数还扣在库外，补回要加库、转报损要等确认，
        // 归属没落定前停用，回头补回就会把件数加回一张停用档案。
        List<WashShortage> openShortages =
                shortages.findByLinenIdAndStatus(l.id, WashShortageService.OPEN);
        if (!openShortages.isEmpty()) {
            WashShortage s = openShortages.get(0);
            throw new BizException("回洗追差 " + s.code + " 还没结案，短少的 " + s.shortQty
                    + " 件没说清去向（补回入库 / 转报损确认），结案再停用");
        }
    }
}
