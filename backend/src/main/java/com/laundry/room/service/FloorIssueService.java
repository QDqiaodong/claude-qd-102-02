package com.laundry.room.service;

import com.laundry.room.dto.BizException;
import com.laundry.room.entity.FloorIssue;
import com.laundry.room.entity.Linen;
import com.laundry.room.repository.FloorIssueRepository;
import com.laundry.room.repository.LinenRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloorIssueService {

    private final FloorIssueRepository issues;
    private final LinenRepository linens;

    public FloorIssueService(FloorIssueRepository issues, LinenRepository linens) {
        this.issues = issues;
        this.linens = linens;
    }

    public List<FloorIssue> list(String floorCode, String status) {
        return issues.findAllByOrderByIdDesc().stream()
                .filter(i -> floorCode == null || floorCode.isEmpty() || floorCode.equals(i.floorCode))
                .filter(i -> status == null || status.isEmpty() || status.equals(i.status))
                .toList();
    }

    @Transactional
    public FloorIssue create(FloorIssue input) {
        if (input.linenId == null) {
            throw new BizException("请选择布草");
        }
        if (input.floorCode == null || input.floorCode.isBlank()) {
            throw new BizException("请填楼层");
        }
        if (input.issueDate == null) {
            throw new BizException("请填送出日期");
        }
        if (input.sendQty == null || input.sendQty <= 0) {
            throw new BizException("送出件数要大于 0");
        }
        Linen linen = linens.findByIdForUpdate(input.linenId)
                .orElseThrow(() -> new BizException("布草不存在"));
        if ("停用".equals(linen.status)) {
            throw new BizException("布草 " + linen.name + " 已经停用了，不能往楼层送");
        }
        if (input.sendQty > linen.stock) {
            throw new BizException("在库只有 " + linen.stock + " 件，送不出 " + input.sendQty
                    + " 件；封存扣下的、追差没结案的，都不能当干净布草往楼层送");
        }
        if (!issues.findByLinenIdAndFloorCodeAndIssueDate(linen.id, input.floorCode.trim(),
                input.issueDate).isEmpty()) {
            throw new BizException(input.floorCode + " 这天这件布草已经登记过了，不用重复送");
        }
        FloorIssue saved = new FloorIssue();
        saved.linenId = linen.id;
        saved.floorCode = input.floorCode.trim();
        saved.issueDate = input.issueDate;
        saved.sendQty = input.sendQty;
        saved.receiver = input.receiver;
        saved.status = "已送出";
        return issues.save(saved);
    }

    @Transactional
    public FloorIssue giveBack(Long id, Integer backQty) {
        FloorIssue issue = issues.findById(id).orElseThrow(() -> new BizException("收发记录不存在"));
        if (!"已送出".equals(issue.status)) {
            throw new BizException("这条收发记录已经收过数了");
        }
        if (backQty == null || backQty <= 0) {
            throw new BizException("请填收回件数");
        }
        if (backQty > issue.sendQty) {
            throw new BizException("收回件数不能多过送出的 " + issue.sendQty + " 件");
        }
        issue.backQty = backQty;
        issue.status = "已收回";
        return issues.save(issue);
    }
}
