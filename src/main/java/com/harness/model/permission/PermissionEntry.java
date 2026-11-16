package com.harness.model.permission;

import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionSource;

public record PermissionEntry(
        PermissionRule rule,
        PermissionBehavior behavior,
        PermissionSource source
) {
}
