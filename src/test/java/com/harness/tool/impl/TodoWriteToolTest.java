package com.harness.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.TestPermissionConfig;
import com.harness.agent.event.AgentEvent;
import com.harness.permission.PermissionBehavior;
import com.harness.tool.FileStateCache;
import com.harness.tool.TodoItem;
import com.harness.tool.TodoStatus;
import com.harness.tool.TodoStore;
import com.harness.tool.ToolContext;
import com.harness.tool.ToolResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.JsonHelper;

import java.util.ArrayList;
import java.util.List;

class TodoWriteToolTest {

    private final List<AgentEvent> published = new ArrayList<>();
    private final TodoWriteTool tool = new TodoWriteTool(event -> published.add((AgentEvent) event));

    private TodoStore store;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        store = new TodoStore(16_384);
        context = TestPermissionConfig.context(new FileStateCache(), store);
    }

    private static TodoItem item(String content, TodoStatus status) {
        return new TodoItem(content, status, "делаю " + content);
    }

    @Test
    void списокПопадаетВПамятьРазговора() {
        ToolResult result = tool.call(
                new TodoWriteTool.Input(List.of(
                        item("прочитать pom.xml", TodoStatus.IN_PROGRESS),
                        item("запустить тесты", TodoStatus.PENDING))),
                context);

        assertThat(result.error()).isFalse();
        assertThat(store.todos()).hasSize(2);
        assertThat(store.todos().getFirst().status()).isEqualTo(TodoStatus.IN_PROGRESS);
    }

    @Test
    void новыйСписокЗамещаетСтарыйЦеликом() {
        tool.call(new TodoWriteTool.Input(List.of(
                item("шаг один", TodoStatus.PENDING),
                item("шаг два", TodoStatus.PENDING),
                item("шаг три", TodoStatus.PENDING))), context);

        tool.call(new TodoWriteTool.Input(List.of(
                item("шаг один", TodoStatus.COMPLETED),
                item("шаг два", TodoStatus.IN_PROGRESS))), context);

        assertThat(store.todos()).extracting(TodoItem::content).containsExactly("шаг один", "шаг два");
    }

    @Test
    void когдаСделаноВсёСписокОчищается() {
        tool.call(new TodoWriteTool.Input(List.of(
                item("шаг один", TodoStatus.COMPLETED),
                item("шаг два", TodoStatus.COMPLETED))), context);

        assertThat(store.todos()).isEmpty();
    }

    /** Память уже пуста, а человеку итог показать надо: в событии то, что прислала модель. */
    @Test
    void вСобытиеУезжаетИтоговыйСписокАНеПустота() {
        tool.call(new TodoWriteTool.Input(List.of(
                item("шаг один", TodoStatus.COMPLETED),
                item("шаг два", TodoStatus.COMPLETED))), context);

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).isInstanceOfSatisfying(
                AgentEvent.TodosUpdated.class,
                event -> assertThat(event.todos()).hasSize(2));
    }

    @Test
    void ответМоделиНеПовторяетСписок() {
        ToolResult result = tool.call(
                new TodoWriteTool.Input(List.of(item("прочитать pom.xml", TodoStatus.PENDING))), context);

        assertThat(result.content())
                .as("список модель только что прислала сама — платить за него дважды незачем")
                .doesNotContain("прочитать pom.xml")
                .contains("Todos have been modified successfully");
    }

    @Test
    void пустойСписокДопустим() {
        tool.call(new TodoWriteTool.Input(List.of(item("шаг", TodoStatus.PENDING))), context);

        ToolResult result = tool.call(new TodoWriteTool.Input(List.of()), context);

        assertThat(result.error()).isFalse();
        assertThat(store.todos()).isEmpty();
    }

    @Test
    void задачаБезТекстаОтвергается() {
        ToolResult result = tool.call(
                new TodoWriteTool.Input(List.of(new TodoItem("  ", TodoStatus.PENDING, "делаю"))), context);

        assertThat(result.error()).isTrue();
        assertThat(store.todos()).isEmpty();
    }

    @Test
    void задачаБезСтатусаОтвергается() {
        ToolResult result = tool.call(
                new TodoWriteTool.Input(List.of(new TodoItem("шаг", null, "делаю шаг"))), context);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("status");
    }

    @Test
    void разрешенияНеСпрашиваются() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.checkPermissions(new TodoWriteTool.Input(List.of()), context).behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
    }

    /** Схема — это обещание модели. Разбор обязан принимать ровно то, что в ней написано. */
    @Test
    void вСхемеСтатусыВТомЖеВидеЧтоИВРазборе() {
        String schema = tool.inputSchema();

        assertThat(schema).contains("PENDING").contains("IN_PROGRESS").contains("COMPLETED");
        for (TodoStatus status : TodoStatus.values()) {
            assertThat(parseStatus(status.name()))
                    .as("схема обещает %s — разбор обязан это принять", status.name())
                    .isEqualTo(status);
        }
    }

    /** Сверх обещанного разбор снисходителен: модель охотно пишет строчными. */
    @Test
    void статусыРазбираютсяВЛюбомРегистре() {
        String fromModel = """
                {"todos":[
                  {"content":"прочитать pom.xml","status":"completed","activeForm":"Читаю pom.xml"},
                  {"content":"запустить тесты","status":"in_progress","activeForm":"Запускаю тесты"},
                  {"content":"собрать отчёт","status":"Pending","activeForm":"Собираю отчёт"}]}""";

        TodoWriteTool.Input input = new JsonHelper().fromJson(fromModel, TodoWriteTool.Input.class);

        assertThat(input.todos()).extracting(TodoItem::status)
                .containsExactly(TodoStatus.COMPLETED, TodoStatus.IN_PROGRESS, TodoStatus.PENDING);
    }

    @Test
    void активнаяФормаПоказываетсяТолькоУТекущейЗадачи() {
        assertThat(item("шаг", TodoStatus.IN_PROGRESS).label()).isEqualTo("делаю шаг");
        assertThat(item("шаг", TodoStatus.PENDING).label()).isEqualTo("шаг");
        assertThat(item("шаг", TodoStatus.COMPLETED).label()).isEqualTo("шаг");
    }

    private static TodoStatus parseStatus(String raw) {
        String json = """
                {"todos":[{"content":"шаг","status":"%s","activeForm":"делаю шаг"}]}""".formatted(raw);
        return new JsonHelper().fromJson(json, TodoWriteTool.Input.class).todos().getFirst().status();
    }
}
