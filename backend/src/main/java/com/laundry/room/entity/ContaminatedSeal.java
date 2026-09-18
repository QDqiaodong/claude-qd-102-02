package com.laundry.room.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 污染布草封存：传染病房 / 污物间退下来的布草，封一桩、扣一批，专洗完成后才准解除还库。 */
@Entity
@Table(name = "contaminated_seal")
public class ContaminatedSeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 封存单号 QZ-xxxx */
    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(name = "linen_id", nullable = false)
    public Long linenId;

    /** 布草退下来的楼层，例如 8F */
    @Column(name = "floor_code", nullable = false, length = 16)
    public String floorCode;

    /** 封存件数，开单时从在库里扣掉的件数 */
    @Column(nullable = false)
    public Integer quantity;

    /** 还没解除的件数；专洗完成解除一件少一件，到 0 这桩封存就关掉了 */
    @Column(name = "remain_qty", nullable = false)
    public Integer remainQty;

    /** 发现时刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "found_at", nullable = false)
    public LocalDateTime foundAt;

    /** 发现人 */
    @Column(nullable = false, length = 32)
    public String founder;

    /** 未解除 / 已解除 */
    @Column(nullable = false, length = 16)
    public String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "released_at")
    public LocalDateTime releasedAt;

    /** 解除人 */
    @Column(length = 32)
    public String releaser;
}
