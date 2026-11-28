package com.harness.tooling;

import com.harness.agent.event.AgentEvent;
import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.permission.PermissionGate;
import org.springframework.ai.util.JsonHelper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final JsonHelper jsonHelper = new JsonHelper();
    private final Map<String, Tool<?>> availableTools = new LinkedHashMap<>();
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ToolContext toolContext;
    private final PermissionGate permissionGate;


    public ToolRegistry(List<Tool<?>> tools, ApplicationEventPublisher applicationEventPublisher, ToolContext toolContext, PermissionGate permissionGate) {
        for (Tool<?> tool : tools) {
            Tool<?> existence = availableTools.put(tool.name(), tool);
            if (existence != null) {
                throw new IllegalArgumentException("Duplicated tool with name: %s".formatted(existence.name()));
            }
        }
        this.applicationEventPublisher = applicationEventPublisher;
        this.toolContext = toolContext;
        this.permissionGate = permissionGate;
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
        if (input == null) return ToolResult.error("you didn't specify input variables");
        try {
            String invalid = tool.validateInput(input, toolContext);
            if (invalid != null) return ToolResult.error(invalid);
            PermissionDecision decision = permissionGate.authorize(tool, input, toolContext);
            if (decision.behavior() != PermissionBehavior.ALLOW) {
                return ToolResult.error("call is forbidden: " + decision.reason());
            }
            return tool.call(input, toolContext);
        } catch (Exception e) {
            return ToolResult.error("%s упал: %s".formatted(tool.name(), e));
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
