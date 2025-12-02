package com.yuqiangdede.lpr;

import com.yuqiangdede.lpr.config.LprProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.yuqiangdede.lpr", "com.yuqiangdede.yolo", "com.yuqiangdede.ocr"})
@EnableConfigurationProperties(LprProperties.class)
public class LprApplication {

    public static void main(String[] args) {
        SpringApplication.run(LprApplication.class, args);
    }
}
