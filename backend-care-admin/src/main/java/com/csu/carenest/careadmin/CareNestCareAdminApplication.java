package com.csu.carenest.careadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 护理端与管理端后端服务启动入口，对应文档固定端口 8082。
 */
@SpringBootApplication
public class CareNestCareAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareNestCareAdminApplication.class, args);
    }
}
