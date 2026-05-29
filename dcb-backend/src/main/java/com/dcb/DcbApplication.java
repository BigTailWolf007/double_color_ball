package com.dcb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 双色球号码分析系统启动类
 */
@SpringBootApplication
@MapperScan("com.dcb.**.mapper")
public class DcbApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcbApplication.class, args);
    }
}
