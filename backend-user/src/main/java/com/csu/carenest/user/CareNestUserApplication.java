package com.csu.carenest.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.csu.carenest.user")
public class CareNestUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareNestUserApplication.class, args);
    }
}
