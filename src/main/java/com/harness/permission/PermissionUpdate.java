package com.harness.permission;

import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionSource;

public sealed interface PermissionUpdate {
    PermissionSource destination();

    record Rule(PermissionRule rule, PermissionBehavior behavior,
                PermissionSource destination) implements PermissionUpdate {
    }

    record Directory(String path) implements PermissionUpdate {

        @Override
        public PermissionSource destination() {
            return PermissionSource.SESSION;
        }
    }

    static Rule rule(PermissionRule rule, PermissionBehavior behavior, PermissionSource source) {
        return new Rule(rule, behavior, source);
    }

    static Directory directory(String path) {
        return new Directory(path);
    }
}
