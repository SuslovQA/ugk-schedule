package ru.ugk.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class UgkScheduleApplication {
    public static void main(String[] args) {
        SpringApplication.run(UgkScheduleApplication.class, args);
    }
}
