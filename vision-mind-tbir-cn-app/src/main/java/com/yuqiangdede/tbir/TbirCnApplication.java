package com.yuqiangdede.tbir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = "com.yuqiangdede")
@EnableScheduling
public class TbirCnApplication {

	public static void main(String[] args)  {
		SpringApplication.run(TbirCnApplication.class, args);
	}

}
