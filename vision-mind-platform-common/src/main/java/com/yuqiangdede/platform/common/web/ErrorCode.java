package com.yuqiangdede.platform.common.web;

public enum ErrorCode {
    SUCCESS("0", "success"),
    BAD_REQUEST("400", "bad request"),
    NOT_FOUND("404", "not found"),
    RESOURCE_MISSING("1001", "resource missing"),
    VALIDATION_FAILED("1002", "validation failed"),
    INTERNAL_ERROR("500", "internal error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
