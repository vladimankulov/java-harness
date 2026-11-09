package com.harness.agent.tool.adapter;

import com.harness.tooling.Tool;
import com.harness.tooling.ToolRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiToolCallbackAdapter {
    private final ToolRegistry toolRegistry;

    public SpringAiToolCallbackAdapter(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<ToolCallback> tools() {
        return toolRegistry.tools()
                .stream()
                .map(this::describe)
                .toList();

    }

    private ToolCallback describe(Tool<?> tool) {
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                throw new IllegalArgumentException("Tools are being called from toolRegistry");
            }
        };
    }
}
