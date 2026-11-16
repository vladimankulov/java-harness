package com.harness.model.permission;

import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.permission.PermissionUpdate;

import java.util.List;

public record PermissionDecision(PermissionBehavior behavior, String reason, boolean askAlways,
                                 List<PermissionUpdate> suggestions) {

    public PermissionDecision {
        suggestions = List.copyOf(suggestions);
    }

    public static PermissionDecision allow(String reason) {
        return new PermissionDecision(PermissionBehavior.ALLOW, reason, false, List.of());
    }

    public static PermissionDecision ask(String reason) {
        return new PermissionDecision(PermissionBehavior.ASK, reason, false, List.of());
    }

    public static PermissionDecision askAlways(String reason) {
        return new PermissionDecision(PermissionBehavior.ASK, reason, true, List.of());
    }

    public static PermissionDecision deny(String reason) {
        return new PermissionDecision(PermissionBehavior.DENY, reason, false, List.of());
    }

    public PermissionDecision withSuggestions(List<PermissionUpdate> suggestions) {
        return new PermissionDecision(behavior, reason, askAlways, suggestions);
    }
}
