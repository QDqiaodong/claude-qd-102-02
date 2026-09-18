package com.laundry.room.dto;

public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
