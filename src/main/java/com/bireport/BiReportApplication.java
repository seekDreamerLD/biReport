package com.bireport;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bireport.mapper")
public class BiReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiReportApplication.class, args);
    }
}
