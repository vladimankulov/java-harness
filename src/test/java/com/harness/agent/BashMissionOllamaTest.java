package com.harness.agent;

import com.harness.agent.property.AgentProperty;
import com.harness.agent.tool.adapter.SpringAiToolCallbackAdapter;
import com.harness.context.ContextManager;
import com.harness.context.GitStatusProvider;
import com.harness.context.ProjectMemoryLoader;
import com.harness.context.SystemPromptBuilder;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.permission.PermissionPrompt;
import com.harness.tooling.Tool;
import com.harness.tooling.ToolRegistry;
import com.harness.tooling.guard.FileStateGuard;
import com.harness.tooling.guard.WorkspaceGuard;
import com.harness.tooling.impl.BashTool;
import fixtures.RecordingEventPublisher;
import fixtures.TestPermissionConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Живая миссия: настоящий Maven запускается только после явного тестового согласия.
 */
@Tag("ollama")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestPermissionConfig.class)
class BashMissionOllamaTest {
    @Autowired
    private ChatModel chatModel;
    @Autowired
    private List<Tool<?>> tools;

    @Test
    void запускаетБыстрыеТестыПослеПодтверждения() {
        AgentProperty properties = new AgentProperty(6, ".", true);
        RecordingEventPublisher events = new RecordingEventPublisher();
        AtomicReference<ToolResult> executed = new AtomicReference<>();
        AtomicInteger confirmations = new AtomicInteger();
        List<Tool<?>> missionTools =
                new ArrayList<>(
                        tools.stream().filter(tool -> !tool.name().equals("Bash")).toList());
        missionTools.add(
                new BashTool(new WorkspaceGuard(properties)) {
                    @Override
                    public ToolResult call(Input input, ToolContext context) {
                        ToolResult result = super.call(input, context);
                        executed.set(result);
                        return result;
                    }
                });
        // Проверка запуска тестов не должна давать модели возможность менять исходники.
        com.harness.config.property.PermissionProperty permissions =
                TestPermissionConfig.properties(
                        PermissionMode.DEFAULT, List.of(), List.of("Edit", "Write"), List.of());
        PermissionPrompt prompt =
                (name, target, reason) -> {
                    if (name.equals("Bash") && (target.strip().contains("mvnw") && target.strip().contains("test"))) {
                        confirmations.incrementAndGet();
                        return PermissionPrompt.Answer.ALLOW_ONCE;
                    }
                    return PermissionPrompt.Answer.DENY;
                };
        ToolRegistry registry =
                TestPermissionConfig.registry(
                        missionTools, events, new FileStateGuard(), permissions, prompt);
        ContextManager context =
                new ContextManager(
                        new GitStatusProvider(properties),
                        new ProjectMemoryLoader(properties),
                        new SystemPromptBuilder(registry, properties),
                        properties);
        Agent agent =
                new Agent(
                        chatModel,
                        events,
                        registry,
                        new SpringAiToolCallbackAdapter(registry),
                        properties,
                        context,
                        new com.harness.tool.TodoStore(16_384));

        String answer = agent.run("Запусти тесты проекта и скажи, всё ли зелено.");

        assertThat(confirmations.get())
                .as("модель должна получить подтверждение команды")
                .isPositive();
        assertThat(executed.get()).as("Bash должен действительно исполнить команду").isNotNull();
        assertThat(executed.get().error()).isFalse();
        assertThat(executed.get().content()).contains("exitCode: 0", "BUILD SUCCESS");
        assertThat(answer).isNotBlank();
    }
}
