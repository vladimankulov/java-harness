package com.harness.permission;

public interface PermissionPrompt {

    PermissionAnswer ask(String toolName, String target, String reason);

    enum PermissionAnswer {
        ALLOW_ONCE, ALLOW_ALWAYS, DENY
    }
}
