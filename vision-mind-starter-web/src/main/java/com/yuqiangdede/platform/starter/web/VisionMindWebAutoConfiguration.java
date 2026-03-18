package com.yuqiangdede.platform.starter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.platform.common.config.VisionMindProperties;
import com.yuqiangdede.platform.common.image.ImageLoader;
import com.yuqiangdede.platform.common.resource.ResourceManifestLoader;
import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import com.yuqiangdede.platform.common.resource.ResourceValidator;
import com.yuqiangdede.platform.common.runtime.ModelRegistry;
import com.yuqiangdede.platform.common.runtime.NativeLibraryManager;
import com.yuqiangdede.platform.common.runtime.OnnxSessionFactory;
import com.yuqiangdede.platform.common.trace.TraceIdFilter;
import com.yuqiangdede.platform.common.web.GlobalExceptionHandler;
import com.yuqiangdede.platform.common.web.RequestLogFilter;
import com.yuqiangdede.platform.starter.web.health.VisionMindHealthController;
import com.yuqiangdede.platform.starter.web.openapi.OpenApiConfiguration;
import com.yuqiangdede.platform.starter.web.startup.ResourceValidationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(VisionMindProperties.class)
@Import(OpenApiConfiguration.class)
public class VisionMindWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper visionMindObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ResourcePathResolver resourcePathResolver(VisionMindProperties properties) {
        return new ResourcePathResolver(properties);
    }

    @Bean
    public ResourceManifestLoader resourceManifestLoader(ObjectMapper objectMapper,
                                                         VisionMindProperties properties,
                                                         ResourcePathResolver pathResolver) {
        return new ResourceManifestLoader(objectMapper, properties, pathResolver);
    }

    @Bean
    public ResourceValidator resourceValidator(ResourcePathResolver pathResolver, ResourceManifestLoader manifestLoader) {
        return new ResourceValidator(pathResolver, manifestLoader);
    }

    @Bean
    public NativeLibraryManager nativeLibraryManager(VisionMindProperties properties) {
        return new NativeLibraryManager(properties);
    }

    @Bean
    public OnnxSessionFactory onnxSessionFactory() {
        return new OnnxSessionFactory();
    }

    @Bean
    public ModelRegistry modelRegistry() {
        return new ModelRegistry();
    }

    @Bean
    public ImageLoader imageLoader() {
        return new ImageLoader();
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistrationBean() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TraceIdFilter());
        bean.setOrder(-100);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RequestLogFilter> requestLogFilterRegistrationBean() {
        FilterRegistrationBean<RequestLogFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestLogFilter());
        bean.setOrder(-90);
        return bean;
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public VisionMindHealthController visionMindHealthController() {
        return new VisionMindHealthController();
    }

    @Bean
    public ResourceValidationRunner resourceValidationRunner(VisionMindProperties properties,
                                                             ResourceValidator validator,
                                                             Environment environment) {
        return new ResourceValidationRunner(properties, validator, environment);
    }
}
