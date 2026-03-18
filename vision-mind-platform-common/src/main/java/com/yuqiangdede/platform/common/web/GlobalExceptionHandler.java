package com.yuqiangdede.platform.common.web;

import com.yuqiangdede.platform.common.exception.VisionMindException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VisionMindException.class)
    public HttpResult<Void> handleVisionMind(VisionMindException ex) {
        return HttpResult.fail(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public HttpResult<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return HttpResult.fail(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public HttpResult<Void> handleBadRequest(IllegalArgumentException ex) {
        return HttpResult.fail(ErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public HttpResult<Void> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return HttpResult.fail(ErrorCode.INTERNAL_ERROR, ex.getMessage());
    }
}
