package com.laundry.room.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 回洗追差：批次收工时回洗少于送洗，短少的件数挂成一张追差单。
 * 结案前这几件不进可领用在库；结案只有两条路——补回入库，或转报损等确认。
 */
@Entity
@Table(name = "wash_shortage")
public class WashShortage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 追差单号 ZC-xxxx */
    @Column(nullable = false, length = 32, unique = true)
    public String code;

    /** 挂在哪张已完成批次上 */
    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    @Column(name = "linen_id", nullable = false)
    public Long linenId;

    /** 送洗件数（开批时的数，快照） */
    @Column(name = "send_qty", nullable = false)
    public Integer sendQty;

    /** 收工时实际回洗件数 */
    @Column(name = "return_qty", nullable = false)
    public Integer returnQty;

    /** 短少件数 = 送洗 - 回洗 */
    @Column(name = "short_qty", nullable = false)
    public Integer shortQty;

    /** 责任楼层或工序 */
    @Column(nullable = false, length = 32)
    public String duty;

    /** 发现人 */
    @Column(nullable = false, length = 32)
    public String founder;

    /** 发现时刻（收工当场） */
    @Column(name = "found_at", nullable = false)
    public LocalDateTime foundAt;

    /** 未结案 / 已结案 */
    @Column(nullable = false, length = 16)
    public String status;

    /** 结案方式：补回入库 / 转报损；未结案为空 */
    @Column(name = "close_type", length = 16)
    public String closeType;

    @Column(name = "closed_at")
    public LocalDateTime closedAt;

    /** 转报损生成的报损记录；确认前追差仍未结案 */
    @Column(name = "loss_id")
    public Long lossId;
}
