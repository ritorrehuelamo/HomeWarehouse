package com.homewarehouse.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.homewarehouse")
public class HomeWarehouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeWarehouseApplication.class, args);
    }
}
