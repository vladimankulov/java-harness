package com.harness.model.tooling;


import com.harness.model.tooling.enums.TodoStatus;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Одна задача плана. Формулировок две: content — пункт списка («Запустить тесты»),
 * activeForm — чем агент занят прямо сейчас («Запускаю тесты»).
 */
public record TodoItem(
        @ToolParam(description = "Imperative form of the task, e.g. \"Run the tests\"")
        String content,

        @ToolParam(description = "Task state. Exactly one task is IN_PROGRESS at a time")
        TodoStatus status,

        @ToolParam(description = "Present continuous form shown while the task runs, e.g. \"Running the tests\"")
        String activeForm) {

    /** Что показать человеку: задача в работе называет себя в настоящем времени. */
    public String label() {
        return status == TodoStatus.IN_PROGRESS ? activeForm : content;
    }
}
