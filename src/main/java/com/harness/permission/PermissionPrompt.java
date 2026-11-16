package com.harness.permission;

import com.harness.model.permission.PermissionDecision;

public interface PermissionPrompt {

    Answer ask(String toolName, String target, PermissionDecision decision);

    enum Answer {
        ALLOW_ONCE, ALLOW_ALWAYS, DENY
    }
}
