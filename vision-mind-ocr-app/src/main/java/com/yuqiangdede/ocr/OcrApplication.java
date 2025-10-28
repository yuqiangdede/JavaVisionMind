package com.yuqiangdede.ocr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.yuqiangdede")
public class OcrApplication {

    public static void main(String[] args) {
        SpringApplication.run(OcrApplication.class, args);
    }
}
