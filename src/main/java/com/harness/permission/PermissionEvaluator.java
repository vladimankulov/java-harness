package com.harness.permission;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.tooling.ToolContext;
import com.harness.tooling.Tool;
import org.springframework.stereotype.Service;

@Service
public class PermissionEvaluator {

    private final OrderKeeper orderKeeper;

    public PermissionEvaluator(OrderKeeper orderKeeper) {
        this.orderKeeper = orderKeeper;
    }

    public <I> PermissionDecision evaluate(Tool<I> tool, I input, ToolContext context) {
        ToolPermissionContext settings = context.permissions();

        PermissionEntry denyFromSettings = settings.wholeToolRule(tool.name(), PermissionBehavior.DENY);
        if (denyFromSettings != null) {
            return PermissionDecision.deny("Denied by rule: %s".formatted(denyFromSettings.rule().toString()));
        }
        PermissionDecision fromTool = tool.checkPermission(input, context);
        if (fromTool.behavior() == PermissionBehavior.DENY) {
            return fromTool;
        }
        PermissionEntry askFromSettings = settings.wholeToolRule(tool.name(), PermissionBehavior.ASK);
        if (askFromSettings != null) {
            return PermissionDecision.ask("Ask user according the rule: %s".formatted(askFromSettings.rule().toString()));
        }

        if (fromTool.askAlways()) {
            return fromTool;
        }

        if (settings.mode() == PermissionMode.BYPASS_PERMISSIONS) {
            return PermissionDecision.allow("Allowed by bypass permissions");
        }
        PermissionEntry allowFromSettings = settings.wholeToolRule(tool.name(), PermissionBehavior.ALLOW);
        if (allowFromSettings != null) {
            return PermissionDecision.ask("Allowed by the rule: %s".formatted(allowFromSettings.rule().toString()));
        }

        return fromTool;
    }
}
