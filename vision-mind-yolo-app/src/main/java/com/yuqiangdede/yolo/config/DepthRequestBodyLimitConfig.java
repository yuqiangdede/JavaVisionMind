package com.yuqiangdede.yolo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只对深度估计接口注册请求体上限，不影响模块内其他上传和检测接口。
 */
@Configuration
public class DepthRequestBodyLimitConfig {

    @Bean
    public FilterRegistrationBean<DepthRequestBodyLimitFilter> depthRequestBodyLimitFilter(
            @Value("${vision-mind.yolo.depth.max-request-bytes:36700160}") int maxRequestBytes,
            @Value("${vision-mind.yolo.depth.max-active-requests:2}") int maxActiveRequests) {
        FilterRegistrationBean<DepthRequestBodyLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DepthRequestBodyLimitFilter(maxRequestBytes, maxActiveRequests));
        registration.setOrder(-80);
        registration.addUrlPatterns(
                "/api/v1/img/depth",
                "/api/v1/img/depth/map",
                "/api/v1/img/depthI",
                "/api/v1/vision/depth",
                "/api/v1/vision/depth/map",
                "/api/v1/vision/depth/preview");
        return registration;
    }
}
