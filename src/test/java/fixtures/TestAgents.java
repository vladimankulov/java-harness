package fixtures;

import com.harness.agent.Agent;
import com.harness.agent.property.AgentProperty;
import com.harness.agent.tool.adapter.SpringAiToolCallbackAdapter;
import com.harness.context.ContextManager;
import com.harness.context.GitStatusProvider;
import com.harness.context.ProjectMemoryLoader;
import com.harness.context.SystemPromptBuilder;
import com.harness.tooling.Tool;
import com.harness.tooling.ToolRegistry;
import com.harness.tooling.guard.FileStateGuard;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public final class TestAgents {

    private TestAgents() {
    }

    public static Agent agent(ChatModel model, RecordingEventPublisher events, Tool<?>... tools) {
        return agent(model, events, new AgentProperty(20, ".", true), tools);
    }

    public static Agent agent(
            ChatModel model, RecordingEventPublisher events, AgentProperty properties, Tool<?>... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools), events, new FileStateGuard());
        return new Agent(model, events, registry, new SpringAiToolCallbackAdapter(registry), properties, context(registry, properties));
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
