package com.yuqiangdede.platform.common.web;

public class HttpResult<T> {

    private String code;
    private String msg;
    private T data;

    public HttpResult() {
    }

    public HttpResult(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> HttpResult<T> success(T data) {
        return new HttpResult<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> HttpResult<T> success(String message, T data) {
        return new HttpResult<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    public static <T> HttpResult<T> fail(ErrorCode errorCode) {
        return new HttpResult<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> HttpResult<T> fail(ErrorCode errorCode, String message) {
        return new HttpResult<>(errorCode.getCode(), message, null);
    }

    public static <T> HttpResult<T> fail(String message) {
        return new HttpResult<>(ErrorCode.INTERNAL_ERROR.getCode(), message, null);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
