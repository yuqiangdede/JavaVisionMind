package com.yuqiangdede.platform.starter.web.health;

import com.yuqiangdede.platform.common.trace.TraceIdHolder;
import com.yuqiangdede.platform.common.web.HttpResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class VisionMindHealthController {

    @GetMapping(value = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResult<Map<String, Object>> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("time", OffsetDateTime.now().toString());
        payload.put("traceId", TraceIdHolder.get());
        return HttpResult.success(payload);
    }
}
