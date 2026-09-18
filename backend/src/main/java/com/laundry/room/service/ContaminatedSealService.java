package com.laundry.room.service;

import com.laundry.room.dto.BizException;
import com.laundry.room.entity.ContaminatedSeal;
import com.laundry.room.entity.Linen;
import com.laundry.room.entity.WashBatch;
import com.laundry.room.repository.ContaminatedSealRepository;
import com.laundry.room.repository.LinenRepository;
import com.laundry.room.repository.WashBatchRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 污染布草封存。
 *
 * 感染控制的硬规矩：
 * 1. 开封存单的同一事务里就把件数从在库扣掉，台账上立刻少这一批；
 * 2. 同一件布草同时只能有一桩未解除的封存，两个人抢开只成一张；
 * 3. 解除前必须挂一张洗法为「专洗」的批次，件数恰好等于还没解除的件数，
 *    专洗用的是已经封存扣下的件数，不走在库够不够的老校验；
 * 4. 专洗走到已完成才准解除，解除时才把件数还回在库。
 */
@Service
public class ContaminatedSealService {

    public static final String ACTIVE = "未解除";
    public static final String RELEASED = "已解除";
    public static final String SPECIAL_WASH = "专洗";

    private final ContaminatedSealRepository seals;
    private final LinenRepository linens;
    private final WashBatchRepository batches;

    public ContaminatedSealService(ContaminatedSealRepository seals,
                                   LinenRepository linens,
                                   WashBatchRepository batches) {
        this.seals = seals;
        this.linens = linens;
        this.batches = batches;
    }

    public List<ContaminatedSeal> list(Long linenId, String status) {
        return seals.findAllByOrderByIdDesc().stream()
                .filter(s -> linenId == null || linenId.equals(s.linenId))
                .filter(s -> status == null || status.isEmpty() || status.equals(s.status))
                .toList();
    }

    /** 开封存单：单子落下的同时，这几件从可领用在库里扣掉。 */
    @Transactional
    public ContaminatedSeal create(ContaminatedSeal input) {
        if (input.linenId == null) {
            throw new BizException("请选要封存的布草");
        }
        if (input.floorCode == null || input.floorCode.isBlank()) {
            throw new BizException("请填涉及楼层");
        }
        if (input.quantity == null || input.quantity <= 0) {
            throw new BizException("封存件数要大于 0");
        }
        if (input.foundAt == null) {
            throw new BizException("请填发现时刻");
        }
        if (input.founder == null || input.founder.isBlank()) {
            throw new BizException("请填发现人");
        }
        // 先锁布草台账行：两个人同时开单，第二个人在这里排队，保证只成一张。
        Linen linen = linens.findByIdForUpdate(input.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        if (input.quantity > linen.stock) {
            throw new BizException("在库只有 " + linen.stock + " 件，封存不了 " + input.quantity + " 件");
        }
        if (!seals.findByLinenIdAndStatus(linen.id, ACTIVE).isEmpty()) {
            throw new BizException("这件布草已经有一桩还没解除的封存占着，同一件不能同时封两批");
        }

        ContaminatedSeal saved = new ContaminatedSeal();
        saved.linenId = linen.id;
        saved.floorCode = input.floorCode.trim();
        saved.quantity = input.quantity;
        saved.remainQty = input.quantity;
        saved.foundAt = input.foundAt;
        saved.founder = input.founder.trim();
        saved.status = ACTIVE;
        // 先拿自增主键再回填 QZ-xxxx 单号，避免并发抢号撞单。
        saved.code = "TMP-" + Long.toHexString(System.nanoTime());
        saved = seals.save(saved);
        saved.code = String.format("QZ-%04d", saved.id);
        saved = seals.save(saved);

        // 封存即扣库：关掉页面再打开，台账在库已经少这么多。
        linen.stock = linen.stock - saved.quantity;
        linens.save(linen);
        return saved;
    }

    /**
     * 挂专洗批次。
     * 专洗不走在库够不够那条老校验——它用的是这桩封存已经扣下的件数，
     * 件数必须恰好等于封存还没解除的件数，由服务端定，前端传了不算。
     */
    @Transactional
    public WashBatch attachSpecialWash(Long sealId, WashBatch input) {
        ContaminatedSeal seal = seals.findByIdForUpdate(sealId)
                .orElseThrow(() -> new BizException("封存单不存在"));
        if (RELEASED.equals(seal.status) || seal.remainQty <= 0) {
            throw new BizException("这桩封存已经解除，不用再挂专洗");
        }
        List<WashBatch> linked = batches.findBySealIdOrderByIdDesc(seal.id);
        boolean hasUnfinished = linked.stream().anyMatch(b -> !"已完成".equals(b.status));
        if (hasUnfinished) {
            throw new BizException("这桩封存的专洗还没洗完，不能再挂一批");
        }
        boolean alreadyDone = linked.stream().anyMatch(b -> "已完成".equals(b.status));
        if (alreadyDone) {
            throw new BizException("这桩封存的专洗已经洗到已完成，直接解除封存就行");
        }

        if (input.code == null || input.code.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        if (batches.existsByCode(input.code.trim())) {
            throw new BizException("批次号 " + input.code + " 已经用过了");
        }
        LocalDate sendDate = input.sendDate != null ? input.sendDate : LocalDate.now();
        LocalDate expectDate = input.expectDate != null ? input.expectDate : sendDate;
        if (expectDate.isBefore(sendDate)) {
            throw new BizException("预计回洗日期不能早于送洗日期");
        }
        Linen linen = linens.findById(seal.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));

        WashBatch batch = new WashBatch();
        batch.code = input.code.trim();
        batch.linenId = linen.id;
        batch.quantity = seal.remainQty;   // 恰好等于还没解除的件数
        batch.sendDate = sendDate;
        batch.expectDate = expectDate;
        batch.washType = SPECIAL_WASH;
        batch.status = "待洗";
        batch.operator = (input.operator == null || input.operator.isBlank())
                ? seal.founder : input.operator.trim();
        batch.sealId = seal.id;
        return batches.save(batch);
    }

    /**
     * 解除封存：不是改一个状态字就完。
     * 必须先有一张件数恰好等于未解除件数、且已走到「已完成」的专洗批次，
     * 然后才在同一事务里把件数还回可领用在库。
     */
    @Transactional
    public ContaminatedSeal release(Long id, String releaser) {
        ContaminatedSeal seal = seals.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("封存单不存在"));
        if (RELEASED.equals(seal.status)) {
            throw new BizException("这桩封存已经解除过了");
        }
        List<WashBatch> linked = batches.findBySealIdOrderByIdDesc(seal.id);
        if (linked.isEmpty()) {
            throw new BizException("还没挂洗法为专洗、件数为 " + seal.remainQty
                    + " 件的洗涤批次，不能解除封存");
        }
        if (linked.stream().anyMatch(b -> !"已完成".equals(b.status))) {
            throw new BizException("专洗还没走到已完成，封存不能解除，件数也不能回到可领用");
        }
        int washedQty = linked.stream()
                .filter(b -> "已完成".equals(b.status))
                .mapToInt(b -> b.quantity)
                .sum();
        if (washedQty != seal.remainQty) {
            throw new BizException("专洗件数 " + washedQty
                    + " 和封存还没解除的 " + seal.remainQty + " 件对不上，不能解除");
        }

        Linen linen = linens.findByIdForUpdate(seal.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        linen.stock = linen.stock + seal.remainQty;
        linens.save(linen);

        seal.status = RELEASED;
        seal.remainQty = 0;
        seal.releasedAt = LocalDateTime.now();
        seal.releaser = (releaser == null || releaser.isBlank()) ? seal.founder : releaser.trim();
        return seals.save(seal);
    }
}
