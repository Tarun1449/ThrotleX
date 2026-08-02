package com.throttlex;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@Slf4j
@SpringBootApplication
public class ThrottlexApplication {
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Setting Time of IST");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ThrottlexApplication.class, args);
    }

}
