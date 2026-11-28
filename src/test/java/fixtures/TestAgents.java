package fixtures;

import com.harness.agent.Agent;
import com.harness.agent.property.AgentProperty;
import com.harness.agent.tool.adapter.SpringAiToolCallbackAdapter;
import com.harness.context.ContextManager;
import com.harness.context.GitStatusProvider;
import com.harness.context.ProjectMemoryLoader;
import com.harness.context.SystemPromptBuilder;
import com.harness.tool.TodoStore;
import com.harness.tooling.Tool;
import com.harness.tooling.ToolRegistry;
import com.harness.tooling.guard.FileStateGuard;
import org.springframework.ai.chat.model.ChatModel;
import java.util.List;

/**
 * Сборка агента для тестов: без Spring-контекста и без сети.
 */
public final class TestAgents {

    private TestAgents() {
    }

    public static Agent agent(ChatModel model, RecordingEventPublisher events, Tool<?>... tools) {
        return agent(model, events, new AgentProperty(20, ".", true), tools);
    }

    static Agent agent(
            ChatModel model,
            RecordingEventPublisher events,
            AgentProperty properties,
            Tool<?>... tools) {
        TodoStore todos = new TodoStore(16_384);
        ToolRegistry registry =
                TestPermissionConfig.registry(
                        List.of(tools),
                        events,
                        new FileStateGuard(),
                        todos,
                        TestPermissionConfig.permissive(),
                        TestPermissionConfig.allowOnce());
        return new Agent(
                model,
                events,
                registry,
                new SpringAiToolCallbackAdapter(registry),
                properties,
                context(registry, properties),
                todos);
    }

    /**
     * Настоящий {@link ContextManager}, а не заглушка: весь контекст — обычные объекты
     * с конструкторами, и собрать их руками дешевле, чем объяснять подмену.
     */
    private static ContextManager context(ToolRegistry registry, AgentProperty properties) {
        return new ContextManager(
                new GitStatusProvider(properties),
                new ProjectMemoryLoader(properties),
                new SystemPromptBuilder(registry, properties),
                properties);
    }
}
