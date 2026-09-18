package com.laundry.room.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 报损赔付：洗坏了、丢了、污损洗不掉的都从这里走。 */
@Entity
@Table(name = "linen_loss")
public class LinenLoss {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "linen_id", nullable = false)
    public Long linenId;

    @Column(name = "loss_date", nullable = false)
    public LocalDate lossDate;

    @Column(nullable = false)
    public Integer quantity;

    /** 破损 / 丢失 / 污损 */
    @Column(nullable = false, length = 16)
    public String reason;

    /** 责任楼层 */
    @Column(name = "duty_floor", length = 16)
    public String dutyFloor;

    /** 待确认 / 已确认 */
    @Column(nullable = false, length = 16)
    public String status;

    /**
     * 从哪张回洗追差转来的；手工登记为空。
     * 追差转入的报损确认时不再扣在库——短少件数在批次收工时就没能回库。
     */
    @Column(name = "shortage_id")
    public Long shortageId;
}
