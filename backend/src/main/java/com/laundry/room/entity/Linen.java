package com.laundry.room.entity;

import jakarta.persistence.*;

/** 布草台账：床单、被套、浴巾这类可重复洗涤的织物。 */
@Entity
@Table(name = "linen")
public class Linen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(nullable = false, length = 64)
    public String name;

    /** 床单 / 被套 / 枕套 / 浴巾 / 地巾 */
    @Column(nullable = false, length = 16)
    public String category;

    /** 规格，例如 200x230 */
    @Column(nullable = false, length = 32)
    public String spec;

    /** 在库件数 */
    @Column(nullable = false)
    public Integer stock;

    /** 低于这个件数要提醒补货 */
    @Column(name = "warn_stock", nullable = false)
    public Integer warnStock;

    /** 在用 / 停用 */
    @Column(nullable = false, length = 16)
    public String status;
}
