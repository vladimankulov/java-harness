package com.harness.permission;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Component
public class CommandInspector {
    private static final Set<String> READ_ONLY = Set.of();
    private static final Set<String> DANGEROUS = Set.of();
    private static final Set<String> UNPARSED_SYNTAX = Set.of();

    public static PermissionDecision check(String command, ToolPermissionContext context) {
        if (!StringUtils.hasText(command)) {
            return PermissionDecision.deny("command is empty");
        }

        command = command.strip();
        PermissionEntry deny = findRule(command, context, PermissionBehavior.DENY);
        if (deny != null) {
            return PermissionDecision.deny("Command %s is denied by rule: %s".formatted(command, deny.rule().toString()));
        }
        PermissionEntry ask = findRule(command, context, PermissionBehavior.ASK);
        if (ask != null) {
            return PermissionDecision.deny("Command %s is asked by rule: %s".formatted(command, ask.rule().toString()));
        }
        if (command.contains("&&")) {
            return checkChain(command, context);
        }
        if (hasUnparsedSyntax(command)) {
            return PermissionDecision.askAlways("Command %s has unparsed syntax".formatted(command));
        }

        String dangerous = findDangerous(command);
        if (StringUtils.hasText(dangerous)) {
            return PermissionDecision.askAlways("Command %s has dangerous syntax".formatted(command));
        }
        PermissionEntry allow = findRule(command, context, PermissionBehavior.ALLOW);
        if (allow != null) {
            return PermissionDecision.deny("Command %s is allowed by rule: %s".formatted(command, allow.rule().toString()));
        }

        if (READ_ONLY.contains(command)) {
            return PermissionDecision.allow("Command %s for reading purposes".formatted(command));
        }
        return PermissionDecision.ask("can we execute the command?").withSuggestions(rememberCommand(command));
    }

    private static PermissionEntry findRule(String command, ToolPermissionContext context, PermissionBehavior permissionBehavior) {
        return null;
    }

    private static PermissionDecision checkChain(String command, ToolPermissionContext context) {
        return PermissionDecision.ask("Mock logic");
    }

    private static boolean hasUnparsedSyntax(String command) {
        return command.chars().anyMatch(UNPARSED_SYNTAX::contains);
    }

    private static String findDangerous(String command) {
        return "has";
    }

    private static List<PermissionUpdate> rememberCommand(String command) {
        if (command.endsWith("*")) {
            return List.of();
        }
        return List.of(PermissionUpdate.rule(new PermissionRule("Bash", command), PermissionBehavior.ALLOW, PermissionSource.LOCAL));
    }

}
