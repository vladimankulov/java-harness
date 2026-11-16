package com.harness.permission;

import static org.assertj.core.api.Assertions.*;

import com.harness.TestPermissionConfig;
import com.harness.agent.AgentProperties;
import com.harness.tool.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class PermissionGateTest {
    @Test
    void файловыйDenyОстанавливаетВызовДоОбщегоAsk() {
        var guard = new WorkspaceGuard(new AgentProperties(20, root.toString(), true));
        var setup =
                TestPermissionConfig.setup(
                        TestPermissionConfig.properties(
                                PermissionMode.DEFAULT,
                                List.of(),
                                List.of("Edit(secret.txt)"),
                                List.of("Write")),
                        guard,
                        new FileStateCache());
        PermissionPrompt prompt =
                (name, target, reason) -> {
                    throw new AssertionError("запрет не должен приводить к вопросу");
                };
        var registry =
                new ToolRegistry(
                        List.of(new com.harness.tool.impl.FileWriteTool(guard)),
                        event -> {},
                        setup.context(),
                        new PermissionGate(
                                setup.evaluator(), prompt, event -> {}));
        assertThat(
                        registry.execute("Write", "{\"filePath\":\"secret.txt\",\"content\":\"x\"}")
                                .error())
                .isTrue();
        assertThat(root.resolve("secret.txt")).doesNotExist();
    }

    @TempDir Path root;

    @Test
    void реестрПроверяетПотомСпрашиваетИТолькоПотомВыполняет() {
        List<String> trace = new ArrayList<>();
        var setup =
                TestPermissionConfig.setup(
                        TestPermissionConfig.properties(
                                PermissionMode.DEFAULT, List.of(), List.of(), List.of()),
                        new WorkspaceGuard(new AgentProperties(20, root.toString(), true)),
                        new FileStateCache());
        Tool<String> tool =
                new Tool<>() {
                    public String name() {
                        return "Probe";
                    }

                    public String description() {
                        return "test";
                    }

                    public Class<String> inputType() {
                        return String.class;
                    }

                    public PermissionDecision checkPermissions(String input, ToolContext context) {
                        trace.add("check");
                        return PermissionDecision.ask("вопрос");
                    }

                    public ToolResult call(String input, ToolContext context) {
                        trace.add("call");
                        return ToolResult.ok("ok");
                    }
                };
        PermissionPrompt prompt =
                (name, target, reason) -> {
                    trace.add("prompt");
                    return PermissionPrompt.Answer.ALLOW_ONCE;
                };
        var handler =
                new PermissionGate(setup.evaluator(), prompt, event -> {});
        var registry = new ToolRegistry(List.of(tool), event -> {}, setup.context(), handler);
        assertThat(registry.execute("Probe", "\"x\"").error()).isFalse();
        assertThat(trace).containsExactly("check", "prompt", "call");
    }

    @Test
    void запретКомандыОстанавливаетВызовДоОбщегоAsk() {
        // Запрет команды приходит из checkPermissions, и Service возвращает его до общего ASK на Bash:
        // человек не может разрешить rm.
        var guard = new WorkspaceGuard(new AgentProperties(20, root.toString(), true));
        var setup = TestPermissionConfig.setup(
                TestPermissionConfig.properties(PermissionMode.DEFAULT, List.of(), List.of("Bash(rm *)"), List.of("Bash")),
                guard, new FileStateCache());
        PermissionPrompt prompt = (name, target, reason) -> {
            throw new AssertionError("запрет не должен приводить к вопросу");
        };
        var registry = new ToolRegistry(
                List.of(new com.harness.tool.impl.BashTool(guard)), event -> {}, setup.context(),
                new PermissionGate(setup.evaluator(), prompt, event -> {}));
        var result = registry.execute("Bash", "{\"command\":\"rm x\"}");
        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("запрещено правилом Bash(rm *)");
    }

    @Test
    void запомнитьПрименяетИПравилоИВнешнийКаталогИзРешения() {
        var guard = new WorkspaceGuard(new AgentProperties(20, root.toString(), true));
        var setup = TestPermissionConfig.setup(
                TestPermissionConfig.properties(PermissionMode.DEFAULT, List.of(), List.of(), List.of()),
                guard, new FileStateCache());
        Path directory = root.resolveSibling("shared");
        var input = new com.harness.tool.impl.FileWriteTool.Input(directory.resolve("report.txt").toString(), "report");
        var tool = new com.harness.tool.impl.FileWriteTool(guard);
        PermissionPrompt prompt = (name, target, decision) -> PermissionPrompt.Answer.ALLOW_ALWAYS;
        var handler = new PermissionGate(setup.evaluator(), prompt, event -> {});

        assertThat(handler.authorize(tool, input, setup.context()).behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
        // Режим не меняется: согласие легло правилом на каталог плюс сам каталог в рабочую область.
        assertThat(setup.evaluator().mode()).isEqualTo(PermissionMode.DEFAULT);
        assertThat(setup.context().permissions().ruleEntries())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.source()).isEqualTo(PermissionSource.SESSION);
                    assertThat(entry.behavior()).isEqualTo(PermissionBehavior.ALLOW);
                    assertThat(entry.rule().toString())
                            .isEqualTo("Edit(//" + directory.toString().replace('\\', '/') + "/**)");
                });
        assertThat(setup.context().permissions().additionalWorkingDirectories())
                .contains(directory);
        assertThat(setup.evaluator().evaluate(tool, input, setup.context()).behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
    }
}
