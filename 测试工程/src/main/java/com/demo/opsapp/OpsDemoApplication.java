package com.demo.opsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsDemoApplication.class, args);
    }
}
