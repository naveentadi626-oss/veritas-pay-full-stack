package com.veritaspay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VeritasPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeritasPayApplication.class, args);
    }
}
