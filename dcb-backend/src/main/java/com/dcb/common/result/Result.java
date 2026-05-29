package com.dcb.common.result;

import lombok.Data;

/**
 * 统一响应体
 */
@Data
public class Result<T> {

    /** 响应码，200=成功 */
    private int code;

    /** 响应消息 */
    private String message;

    /** 响应数据 */
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
