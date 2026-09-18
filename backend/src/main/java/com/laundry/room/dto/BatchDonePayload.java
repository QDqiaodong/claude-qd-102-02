package com.laundry.room.dto;

/**
 * 批次收工时随 done 动作带上来的信息。
 * returnQty 缺省等于送洗件数（全数回库）；
 * 回洗少于送洗时，责任楼层或工序、发现人必须当场填齐。
 */
public class BatchDonePayload {

    /** 实际回洗件数 */
    public Integer returnQty;

    /** 责任楼层或工序（有短少时必填） */
    public String duty;

    /** 发现人（有短少时必填） */
    public String founder;
}
