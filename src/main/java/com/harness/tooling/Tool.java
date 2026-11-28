package com.harness.tooling;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.permission.PermissionUpdate;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.util.List;

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

    default @Nullable String validateInput(I input, ToolContext context) {
        return null;
    }

    default PermissionDecision checkPermission(I input, ToolContext context) {
        if (isReadOnly()) {
            return PermissionDecision.allow("tool only reads");
        } else {
            return PermissionDecision.ask("tool changes state")
                    .withSuggestions(
                            List.of(
                                    PermissionUpdate.rule(
                                            new PermissionRule(name(), null),
                                            PermissionBehavior.ALLOW,
                                            PermissionSource.LOCAL
                                    )
                            )
                    );
        }
    }
}
