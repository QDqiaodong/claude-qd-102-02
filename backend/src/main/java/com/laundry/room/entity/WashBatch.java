package com.laundry.room.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 洗涤批次：一批布草送去洗，洗完再回来。 */
@Entity
@Table(name = "wash_batch")
public class WashBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(name = "linen_id", nullable = false)
    public Long linenId;

    @Column(nullable = false)
    public Integer quantity;

    @Column(name = "send_date", nullable = false)
    public LocalDate sendDate;

    /** 预计回洗日期 */
    @Column(name = "expect_date", nullable = false)
    public LocalDate expectDate;

    /** 常规 / 强化 / 专洗（专洗只能从污染封存挂批，走封存件数） */
    @Column(name = "wash_type", nullable = false, length = 16)
    public String washType;

    /** 待洗 / 洗涤中 / 待烘干 / 已完成 */
    @Column(nullable = false, length = 16)
    public String status;

    @Column(nullable = false, length = 32)
    public String operator;

    /** 专洗批次挂在哪桩污染封存上；常规 / 强化批次为空 */
    @Column(name = "seal_id")
    public Long sealId;

    /** 收工时实际回洗件数；没走到已完成为空。短少件数 = quantity - returnQty，挂在追差上 */
    @Column(name = "return_qty")
    public Integer returnQty;
}
