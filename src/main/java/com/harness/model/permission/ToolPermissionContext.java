package com.harness.model.permission;

import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public record ToolPermissionContext(
        PermissionMode mode,
        List<PermissionEntry> permissionEntries,
        List<Path> additionalWorkingDirectories
) {

    public ToolPermissionContext {
        permissionEntries = List.copyOf(permissionEntries);
        additionalWorkingDirectories = List.copyOf(additionalWorkingDirectories);
    }

    public static ToolPermissionContext empty() {
        return new ToolPermissionContext(PermissionMode.DEFAULT, List.of(), List.of());
    }

    public @Nullable PermissionEntry wholeToolRule(String toolName, PermissionBehavior behavior) {
        for (PermissionEntry permissionEntry : this.permissionEntries) {
            if (permissionEntry.behavior().equals(behavior) && permissionEntry.rule().argumentPattern() == null && permissionEntry.rule().appliesTo(toolName)) {
                return permissionEntry;
            }
        }
        return null;
    }
}
