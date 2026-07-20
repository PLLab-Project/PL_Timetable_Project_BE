package com.example.pl_timetable_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PlTimetableProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlTimetableProjectApplication.class, args);
    }

}
