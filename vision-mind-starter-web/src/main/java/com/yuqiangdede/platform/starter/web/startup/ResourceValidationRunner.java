package com.yuqiangdede.platform.starter.web.startup;

import com.yuqiangdede.platform.common.config.VisionMindProperties;
import com.yuqiangdede.platform.common.resource.ResourceValidator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

public class ResourceValidationRunner implements ApplicationRunner {

    private final VisionMindProperties properties;
    private final ResourceValidator resourceValidator;
    private final Environment environment;

    public ResourceValidationRunner(VisionMindProperties properties,
                                    ResourceValidator resourceValidator) {
        this(properties, resourceValidator, null);
    }

    public ResourceValidationRunner(VisionMindProperties properties,
                                    ResourceValidator resourceValidator,
                                    Environment environment) {
        this.properties = properties;
        this.resourceValidator = resourceValidator;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getResource().isValidateOnStartup()) {
            return;
        }
        String appName = "default";
        if (environment != null) {
            appName = environment.getProperty("spring.application.name", "default");
        }
        resourceValidator.validateOrThrow(appName);
    }
}
