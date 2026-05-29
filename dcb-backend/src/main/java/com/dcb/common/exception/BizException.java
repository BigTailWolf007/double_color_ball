package com.dcb.common.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final String message;

    public BizException(String message) {
        super(message);
        this.message = message;
    }
}
