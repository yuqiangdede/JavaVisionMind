package com.yuqiangdede.platform.common.web;

public final class HttpResultCompat {

    private HttpResultCompat() {
    }

    public static <T> HttpResult<T> fromLegacy(Object legacyResult) {
        if (legacyResult == null) {
            return HttpResult.fail("empty result");
        }
        try {
            Object code = legacyResult.getClass().getMethod("getCode").invoke(legacyResult);
            Object msg = legacyResult.getClass().getMethod("getMsg").invoke(legacyResult);
            @SuppressWarnings("unchecked")
            T data = (T) legacyResult.getClass().getMethod("getData").invoke(legacyResult);
            return new HttpResult<>(
                    code == null ? ErrorCode.INTERNAL_ERROR.getCode() : code.toString(),
                    msg == null ? null : msg.toString(),
                    data
            );
        } catch (Exception ex) {
            return HttpResult.fail("legacy result convert failed: " + ex.getMessage());
        }
    }
}
