package com.skylanka.air;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkyLankaAirApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                SkyLankaAirApplication.class,
                args
        );
    }
}