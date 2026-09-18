package com.laundry.room.service;

import com.laundry.room.dto.BizException;
import com.laundry.room.entity.Linen;
import com.laundry.room.repository.ContaminatedSealRepository;
import com.laundry.room.repository.LinenRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinenService {

    private final LinenRepository linens;
    private final ContaminatedSealRepository seals;

    public LinenService(LinenRepository linens, ContaminatedSealRepository seals) {
        this.linens = linens;
        this.seals = seals;
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
        Linen l = linens.findById(id).orElseThrow(() -> new BizException("布草不存在"));
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
            if ("停用".equals(input.status) && l.stock != null && l.stock > 0) {
                throw new BizException("这件布草在库还有 " + l.stock + " 件，处理完才能停用");
            }
            if ("停用".equals(input.status)
                    && !seals.findByLinenIdAndStatus(id, ContaminatedSealService.ACTIVE).isEmpty()) {
                throw new BizException("这件布草还有没解除的污染封存，等专洗完解除封存再停用");
            }
            l.status = input.status;
        }
        return linens.save(l);
    }
}
