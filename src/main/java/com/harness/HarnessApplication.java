package com.harness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа. Именно этот main мы запускаем в начале каждого урока —
 * сначала чтобы увидеть, чего агенту не хватает, потом чтобы убедиться, что фича работает.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarnessApplication.class, args);
    }
}
