package com.yuqiangdede.platform.common.exception;

import com.yuqiangdede.platform.common.web.ErrorCode;

public class VisionMindException extends RuntimeException {

    private final ErrorCode errorCode;

    public VisionMindException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
