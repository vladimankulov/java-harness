package com.harness.tooling;

import com.harness.agent.event.AgentEvent;
import com.harness.model.tooling.ToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.util.JsonHelper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final JsonHelper jsonHelper = new JsonHelper();
    private final Map<String, Tool<?>> availableTools = new HashMap<>();
    private final ApplicationEventPublisher applicationEventPublisher;

    public ToolRegistry(List<Tool<?>> tools, ApplicationEventPublisher applicationEventPublisher) {
        for (Tool<?> tool : tools) {
            Tool<?> existence = availableTools.put(tool.name(), tool);
            if (existence != null) {
                throw new IllegalArgumentException("Duplicated tool with name: %s".formatted(existence.name()));
            }
        }
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public List<Tool<?>> tools() {
        return List.copyOf(availableTools.values());
    }

    public ToolResult execute(String toolName, String args) {
        Tool<?> tool = availableTools.get(toolName);
        if (tool == null) {
            return ToolResult.error("Tool by name: %s not found".formatted(args));
        }
        applicationEventPublisher.publishEvent(new AgentEvent.ToolCallStarted(tool.name(), describeCall(tool, args)));
        Instant start = Instant.now();
        ToolResult toolResult = invoke(tool, args);

        applicationEventPublisher.publishEvent(
                new AgentEvent.ToolCallFinished(
                        toolName,
                        toolResult.error(),
                        toolResult.preview(100),
                        Duration.between(start, Instant.now())));
        return toolResult;
    }

    private <I> ToolResult invoke(Tool<I> tool, String argumentsJson) {
        I input;
        try {
            input = jsonHelper.fromJson(argumentsJson, tool.inputType());
        } catch (Exception e) {
            return ToolResult.error("Failed to read arguments for tool: %s, expected: %s, err: %s".formatted(tool.name(), tool.inputSchema(), e.getMessage()));
        }
        try {
            return tool.call(input);
        } catch (Exception e) {
            return ToolResult.error("Failed to execute the tool: %s, err: %s".formatted(tool.name(), e.getMessage()));
        }
    }

    private <I> String describeCall(Tool<I> tool, String argumentsJson) {
        try {
            String described = tool.renderToolUsage(jsonHelper.fromJson(argumentsJson, tool.inputType()));
            return StringUtils.hasText(described) ? described : "";
        } catch (Exception e) {
            return "";
        }
    }
}
