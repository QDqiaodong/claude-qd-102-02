package com.laundry.room.service;

import com.laundry.room.dto.BizException;
import com.laundry.room.entity.Linen;
import com.laundry.room.entity.LinenLoss;
import com.laundry.room.entity.WashShortage;
import com.laundry.room.repository.LinenLossRepository;
import com.laundry.room.repository.LinenRepository;
import com.laundry.room.repository.WashShortageRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回洗追差。
 *
 * 库房认的硬规矩：
 * 1. 追差一挂上，短少的件数就扣住在库外——楼层收发只认在库，自然领不到这几件；
 * 2. 一张已完成批次同时只能有一张未结案追差（数据库唯一索引兜底）；
 * 3. 结案只两条路：补回入库（件数当场加回在库），或转报损等确认
 *    （确认前追差仍未结案，件数还是不进在库）。
 */
@Service
public class WashShortageService {

    public static final String OPEN = "未结案";
    public static final String CLOSED = "已结案";
    public static final String CLOSE_REPLENISH = "补回入库";
    public static final String CLOSE_TO_LOSS = "转报损";

    private final WashShortageRepository shortages;
    private final LinenRepository linens;
    private final LinenLossRepository losses;

    public WashShortageService(WashShortageRepository shortages, LinenRepository linens,
                               LinenLossRepository losses) {
        this.shortages = shortages;
        this.linens = linens;
        this.losses = losses;
    }

    public List<WashShortage> list(Long linenId, String status) {
        return shortages.findAllByOrderByIdDesc().stream()
                .filter(s -> linenId == null || linenId.equals(s.linenId))
                .filter(s -> status == null || status.isEmpty() || status.equals(s.status))
                .toList();
    }

    /**
     * 结案路径一：短少的件数找回来了，当场加回可领用在库。
     * 行锁串行化，两个人同时点补回只成一次。
     */
    @Transactional
    public WashShortage replenish(Long id) {
        WashShortage s = shortages.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("追差单不存在"));
        if (!OPEN.equals(s.status)) {
            throw new BizException("这张追差已经结案了（" + s.closeType + "），不用重复处理");
        }
        if (s.lossId != null) {
            throw new BizException("这张追差已经转进报损（报损 #" + s.lossId + "）在等确认，"
                    + "确认前不能再补回；短少的 " + s.shortQty + " 件一直没进在库");
        }
        Linen linen = linens.findByIdForUpdate(s.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        // 补回才是在库按全额涨的时刻：收工时只回了 returnQty，现在把短少的也还上。
        linen.stock = linen.stock + s.shortQty;
        linens.save(linen);

        s.status = CLOSED;
        s.closeType = CLOSE_REPLENISH;
        s.closedAt = LocalDateTime.now();
        return shortages.save(s);
    }

    /**
     * 结案路径二：短少补不回来，转进报损赔付走确认。
     * 只生成待确认报损单，追差保持未结案——报损确认那一刻才真正结案。
     * 件数不从在库出（收工时就没让它回库），所以不受「同日同布草只报一次」
     * 和「报损件数不能超过在库」这两条手工报损的校验限制。
     */
    @Transactional
    public WashShortage toLoss(Long id, String reason) {
        WashShortage s = shortages.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("追差单不存在"));
        if (!OPEN.equals(s.status)) {
            throw new BizException("这张追差已经结案了（" + s.closeType + "），不用再转报损");
        }
        if (s.lossId != null) {
            throw new BizException("这张追差已经转进报损（报损 #" + s.lossId + "）在等确认，"
                    + "确认前短少的 " + s.shortQty + " 件不进在库，也不用再转一次");
        }
        LinenLoss loss = new LinenLoss();
        loss.linenId = s.linenId;
        loss.lossDate = LocalDate.now();
        loss.quantity = s.shortQty;
        loss.reason = (reason == null || reason.isBlank()) ? "丢失" : reason.trim();
        loss.dutyFloor = s.duty;
        loss.status = "待确认";
        loss.shortageId = s.id;
        loss = losses.save(loss);

        s.lossId = loss.id;
        return shortages.save(s);
    }
}
