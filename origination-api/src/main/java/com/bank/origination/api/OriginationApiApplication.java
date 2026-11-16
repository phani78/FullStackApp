package com.bank.origination.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OriginationApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OriginationApiApplication.class, args);
    }
}
