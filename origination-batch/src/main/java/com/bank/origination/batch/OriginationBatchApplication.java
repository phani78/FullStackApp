package com.bank.origination.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OriginationBatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(OriginationBatchApplication.class, args);
    }
}
