package fixtures;

import com.harness.agent.Agent;
import com.harness.agent.property.AgentProperty;
import com.harness.agent.tool.adapter.SpringAiToolCallbackAdapter;
import com.harness.tooling.Tool;
import com.harness.tooling.ToolRegistry;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public final class TestAgents {

    private TestAgents() {
    }

    public static Agent agent(ChatModel model, RecordingEventPublisher events, Tool<?>... tools) {
        return agent(model, events, new AgentProperty(20, "."), tools);
    }

    public static Agent agent(
            ChatModel model, RecordingEventPublisher events, AgentProperty properties, Tool<?>... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools), events);
        return new Agent(model, events, registry, new SpringAiToolCallbackAdapter(registry), properties);
    }
}
