package com.harness.tool;

import com.harness.model.tooling.TodoItem;
import com.harness.model.tooling.ToolResult;
import com.harness.model.tooling.enums.TodoStatus;
import com.harness.tooling.TodoStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Напоминание о плане: когда планировщик его отдаёт. Куда его кладёт агент — в AgentLoopTest.
 */
class PlanReminderTest {

    private final TodoStore store = new TodoStore(16_384);

    private static TodoItem item(String content, TodoStatus status) {
        return new TodoItem(content, status, "делаю " + content);
    }

    private void plan() {
        store.replace(List.of(
                item("прочитать файлы", TodoStatus.IN_PROGRESS),
                item("записать число", TodoStatus.PENDING),
                item("доложить", TodoStatus.PENDING)));
    }

    private static String x(int chars) {
        return "x".repeat(chars);
    }

    /**
     * Что агент положит в разговор после хода, в котором инструмент вернул такой текст.
     */
    private Optional<String> afterTool(String text) {
        store.countToolResult("{}", ToolResult.ok(text));
        return store.reminderAfterToolResults();
    }

    @Test
    void безПланаНапоминатьНечего() {
        assertThat(afterTool(x(50_000))).isEmpty();
        store.countToolResult("{}", ToolResult.error("упал"));
        assertThat(store.reminderAfterToolResults()).isEmpty();
    }

    @Test
    void короткийОтветПланНеВытесняет() {
        plan();

        assertThat(afterTool(x(5_000))).isEmpty();
    }

    /**
     * Каждый ответ по отдельности невелик, а вместе они вытесняют план из окна.
     */
    @Test
    void напоминаниеКогдаНабралосьМногоТекста() {
        plan();

        assertThat(afterTool(x(7_000))).isEmpty();
        assertThat(afterTool(x(7_000))).get().asString()
                .startsWith("Remember about the plan")
                .contains("1. [IN_PROGRESS] прочитать файлы")
                .contains("2. [PENDING] записать число")
                .contains("«прочитать файлы», then «записать число»");
    }

    /**
     * Три чтения за один ход — одно напоминание после всех трёх, а не три.
     */
    @Test
    void несколькоОтветовЗаХодОдноНапоминание() {
        plan();
        store.countToolResult("{}", ToolResult.ok(x(14_000)));
        store.countToolResult("{}", ToolResult.ok(x(14_000)));

        assertThat(store.reminderAfterToolResults()).isPresent();
        assertThat(store.reminderAfterToolResults()).isEmpty();
    }

    @Test
    void послеНапоминанияСчётНачинаетсяЗаново() {
        plan();
        afterTool(x(14_000));

        assertThat(afterTool(x(5_000))).isEmpty();
    }

    /**
     * Модель только что прислала план сама: она его видит, считать заново.
     */
    @Test
    void новыйПланСбрасываетСчёт() {
        plan();
        store.countToolResult("{}", ToolResult.ok(x(10_000)));
        plan();

        assertThat(afterTool(x(5_000))).isEmpty();
    }

    /**
     * Ответ инструмента короткий, но модель прислала много: у Write в аргументах весь файл.
     */
    @Test
    void присланноеМодельюТожеВСчёте() {
        plan();
        store.countToolResult(x(14_000), ToolResult.ok("файл записан"));

        assertThat(store.reminderAfterToolResults()).get().asString().startsWith("Remember about the plan");
    }

    /**
     * После ошибки модель решает, что делать дальше, — пусть решает, глядя в план.
     */
    @Test
    void ошибкаИнструментаНапоминаетСразу() {
        plan();
        store.countToolResult("{}", ToolResult.error("файл не найден"));

        assertThat(store.reminderAfterToolResults()).get().asString().contains("Remember about the plan");
    }

    /**
     * Плана нет — мягко напоминаем про TodoWrite, как Claude Code. Сам план тут показывать нечего.
     */
    @Test
    void безПланаРепликаЧеловекаПолучаетМягкоеНапоминание() {
        assertThat(store.reminderAfterUserMessage()).get().asString()
                .startsWith("The TodoWrite tool")
                .doesNotContain("Remember about the plan");
    }

    /**
     * План есть — после реплики человека напоминать нечего: о плане напомнят ответы инструментов.
     */
    @Test
    void сПланомПослеРепликиНапоминанияНет() {
        plan();

        assertThat(store.reminderAfterUserMessage()).isEmpty();
    }
}
