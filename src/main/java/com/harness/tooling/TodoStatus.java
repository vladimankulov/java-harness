package com.harness.tool;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    /**
     * Схема обещает модели имена констант: PENDING, IN_PROGRESS, COMPLETED.
     * Но модель легко пришлёт "in_progress" — регистр прощаем, чтобы не терять вызов из-за буквы.
     */
    @JsonCreator
    static TodoStatus fromJson(String raw) {
        return valueOf(raw.strip().toUpperCase(Locale.ROOT));
    }
}