package com.yuqiangdede.asr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.yuqiangdede")
public class AsrApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsrApplication.class, args);
    }
}
