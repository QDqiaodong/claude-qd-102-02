package com.laundry.room.service;

import com.laundry.room.dto.BizException;
import com.laundry.room.entity.Linen;
import com.laundry.room.entity.LinenLoss;
import com.laundry.room.entity.WashShortage;
import com.laundry.room.repository.ContaminatedSealRepository;
import com.laundry.room.repository.LinenLossRepository;
import com.laundry.room.repository.LinenRepository;
import com.laundry.room.repository.WashShortageRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinenLossService {

    private final LinenLossRepository losses;
    private final LinenRepository linens;
    private final ContaminatedSealRepository seals;
    private final WashShortageRepository shortages;

    public LinenLossService(LinenLossRepository losses, LinenRepository linens,
                            ContaminatedSealRepository seals, WashShortageRepository shortages) {
        this.losses = losses;
        this.linens = linens;
        this.seals = seals;
        this.shortages = shortages;
    }

    public List<LinenLoss> list(Long linenId, String status) {
        return losses.findAllByOrderByIdDesc().stream()
                .filter(l -> linenId == null || linenId.equals(l.linenId))
                .filter(l -> status == null || status.isEmpty() || status.equals(l.status))
                .toList();
    }

    @Transactional
    public LinenLoss create(LinenLoss input) {
        if (input.linenId == null) {
            throw new BizException("请选择布草");
        }
        if (input.lossDate == null) {
            throw new BizException("请填报损日期");
        }
        if (input.quantity == null || input.quantity <= 0) {
            throw new BizException("报损件数要大于 0");
        }
        Linen linen = linens.findById(input.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        if (input.quantity > linen.stock) {
            throw new BizException("在库只有 " + linen.stock + " 件，报损不了 " + input.quantity + " 件");
        }
        if (!losses.findByLinenIdAndLossDate(linen.id, input.lossDate).isEmpty()) {
            throw new BizException("这件布草 " + input.lossDate + " 已经报过损了");
        }
        LinenLoss saved = new LinenLoss();
        saved.linenId = linen.id;
        saved.lossDate = input.lossDate;
        saved.quantity = input.quantity;
        saved.reason = (input.reason == null || input.reason.isBlank()) ? "破损" : input.reason;
        saved.dutyFloor = input.dutyFloor;
        saved.status = "待确认";
        saved.shortageId = null;
        return losses.save(saved);
    }

    @Transactional
    public LinenLoss confirm(Long id) {
        LinenLoss loss = losses.findById(id).orElseThrow(() -> new BizException("报损记录不存在"));
        if (!"待确认".equals(loss.status)) {
            throw new BizException("这条报损已经确认过了");
        }
        if (loss.shortageId != null) {
            return confirmFromShortage(loss);
        }
        Linen linen = linens.findByIdForUpdate(loss.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        if (!seals.findByLinenIdAndStatus(loss.linenId, ContaminatedSealService.ACTIVE).isEmpty()) {
            throw new BizException("这件布草还有没解除的污染封存，专洗没完成前不能报损扣这几件");
        }
        if (loss.quantity > linen.stock) {
            throw new BizException("在库只剩 " + linen.stock + " 件，扣不掉 " + loss.quantity + " 件");
        }
        linen.stock = linen.stock - loss.quantity;
        linens.save(linen);
        loss.status = "已确认";
        return losses.save(loss);
    }

    /**
     * 追差转入的报损确认：短少件数在批次收工时就没能回库，
     * 这里确认只走状态、联动追差结案，不能再从在库扣第二遍；
     * 也正因为不碰在库，未解除封存那条拦截用不上。
     */
    private LinenLoss confirmFromShortage(LinenLoss loss) {
        WashShortage s = shortages.findByIdForUpdate(loss.shortageId)
                .orElseThrow(() -> new BizException("来源追差单不存在，找库房核对"));
        if (!WashShortageService.OPEN.equals(s.status)) {
            throw new BizException("来源追差 " + s.code + " 已经结案了，这条报损状态对不上，找库房核对");
        }
        loss.status = "已确认";
        losses.save(loss);

        s.status = WashShortageService.CLOSED;
        s.closeType = WashShortageService.CLOSE_TO_LOSS;
        s.closedAt = LocalDateTime.now();
        shortages.save(s);
        return loss;
    }
}
