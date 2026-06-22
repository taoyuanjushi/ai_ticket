package com.example.hello_demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 项目启动入口。
 * @MapperScan 用来扫描 mapper 包，让 MyBatis-Plus 能创建 Mapper Bean。
 */
@MapperScan("com.example.hello_demo.mapper")
@SpringBootApplication
public class HelloDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelloDemoApplication.class, args);
	}

}
