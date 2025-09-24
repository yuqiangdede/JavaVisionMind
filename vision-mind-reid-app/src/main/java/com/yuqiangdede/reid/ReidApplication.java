package com.yuqiangdede.reid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = "com.yuqiangdede")
@EnableScheduling
public class ReidApplication {

	public static void main(String[] args)  {
		SpringApplication.run(ReidApplication.class, args);
	}

}
