package com.laundry.room.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 楼层收发：某天某个楼层送出去多少件，收回来多少件。 */
@Entity
@Table(name = "floor_issue")
public class FloorIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "linen_id", nullable = false)
    public Long linenId;

    /** 楼层编号，例如 8F */
    @Column(name = "floor_code", nullable = false, length = 16)
    public String floorCode;

    @Column(name = "issue_date", nullable = false)
    public LocalDate issueDate;

    /** 送出去的件数 */
    @Column(name = "send_qty", nullable = false)
    public Integer sendQty;

    /** 收回来的件数，没回来时为空 */
    @Column(name = "back_qty")
    public Integer backQty;

    @Column(nullable = false, length = 32)
    public String receiver;

    /** 已送出 / 已收回 */
    @Column(nullable = false, length = 16)
    public String status;
}
