package com.harness.tooling;

import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

public interface Tool<I> {
    String name();

    String description();

    Class<I> inputType();

    ToolResult call(I input, ToolContext context);

    default boolean isReadOnly() {
        return true;
    }

    default String renderToolUsage(I input) {
        return "";
    }

    default String inputSchema() {
        return JsonSchemaGenerator.generateForType(inputType());
    }

}
