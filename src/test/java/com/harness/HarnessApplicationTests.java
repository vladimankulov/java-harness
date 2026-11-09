package com.harness;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Самый дешёвый и самый полезный тест в Spring-проекте: контекст поднимается.
 * Ловит опечатки в конфиге, циклы в зависимостях и неоднозначные бины ещё до запуска.
 */
@SpringBootTest
@ActiveProfiles("test")
class HarnessApplicationTests {

    @Test
    void contextLoads() {
    }
}
