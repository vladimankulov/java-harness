package com.harness.permission;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.tooling.guard.WorkspaceGuard;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class PathInspector {

    private static final Set<String> PROTECTED_DIRS = Set.of();
    private static final Set<String> PROTECTED_FILES = Set.of();

    public static PermissionDecision checkRead(WorkspaceGuard guard, String rawPath, ToolPermissionContext context) {
        return check(guard, rawPath, context, false);
    }

    public static PermissionDecision checkWrite(WorkspaceGuard guard, String rawPath, ToolPermissionContext context) {
        return check(guard, rawPath, context, true);
    }

    private static PermissionDecision check(WorkspaceGuard guard, String rawPath, ToolPermissionContext context, boolean write) {
        if (isNetwork(rawPath)) {
            return PermissionDecision.askAlways("Attempt to access network path: " + rawPath);
        }

        Path path = guard.resolve(rawPath);
        PermissionEntry deny = find(guard, path, context, PermissionBehavior.DENY, write);
        if (deny != null) {
            return PermissionDecision.deny("Access to the filePath %s denied by the rule: %s".formatted(path, deny.rule().toString()));
        }

        PermissionEntry ask = find(guard, path, context, PermissionBehavior.ASK, write);
        if (ask != null) {
            return PermissionDecision.ask("Access to the filePath %s asked by the rule: %s".formatted(path, ask.rule().toString()));
        }

        if (write && isProtected(guard, path)) {
            return PermissionDecision.askAlways("Access to the filePath: %s is protected".formatted(path));
        }

        boolean inside = isWorkingDirectories(guard, path, context);

        if (inside && (!write || context.mode() == PermissionMode.ACCEPT_EDITS)) {
            return PermissionDecision.allow("Access to the filePath: %s is granted because filepath inside working directories".formatted(path));
        }
        PermissionEntry allow = find(guard, path, context, PermissionBehavior.ALLOW, write);
        if (allow != null) {
            return PermissionDecision.ask("Access to the filePath %s allowd by the rule: %s".formatted(path, allow.rule().toString()));
        }

        return PermissionDecision.ask((write ? "changing file:" : "reading file:") + path + "outside of a working directories")
                .withSuggestions(suggestions(path, write, inside));


    }

    private List<PermissionUpdate> suggestions(Path path, boolean write, boolean inside) {
        List<PermissionUpdate> suggestions = new ArrayList<>();
        Path directory = Files.isDirectory(path) ? path : path.getParent();
        if (directory == null || directory.getParent() == null || directory.toString().matches(".*[.?].*")) {
            return suggestions;
        }
        String folder = "//" + directory.toString().replace("\\", "/") + "/**";
        suggestions.add(PermissionUpdate.rule(new PermissionRule(write ? "Edit" : "Read", folder), PermissionBehavior.ALLOW, PermissionSource.SESSION));
        if (write && !inside) {
            suggestions.add(PermissionUpdate.directory(directory.toString()));
        }

        return suggestions;
    }

    private static boolean isNetwork(String rawPath) {
        return StringUtils.hasText(rawPath) && (rawPath.startsWith("\\\\") || rawPath.startsWith("//"));
    }

    private static boolean isProtected(WorkspaceGuard guard, Path path) {
        return guard.pathsForCheck(path)
                .stream()
                .anyMatch(p -> {
                    int nameCount = p.getNameCount();

                    for (int i = 0; i < nameCount; i++) {
                        String segment = p.getName(i).toString();

                        if (segment.equalsIgnoreCase(".claude") && (i + 1 < nameCount && p.getName(i + 1).toString().equalsIgnoreCase("workspace"))) {
                            continue;
                        }
                        if (PROTECTED_DIRS.contains(segment)) {
                            return true;
                        }
                        if (segment.equalsIgnoreCase(".config") && (i + 1 < nameCount && p.getName(i + 1).toString().equalsIgnoreCase(".git"))) {
                            return true;
                        }
                    }
                    return p.getFileName() != null && PROTECTED_FILES.contains(p.getFileName().toString().toLowerCase(Locale.ROOT));
                });
    }

    private static boolean isWorkingDirectories(WorkspaceGuard guard, Path path, ToolPermissionContext context) {
        List<Path> roots = new ArrayList<>(context.additionalWorkingDirectories());
        roots.add(guard.getRoot());
        return guard.pathsForCheck(path)
                .stream().allMatch(r -> roots.stream().anyMatch(p -> p.startsWith(r.toAbsolutePath().normalize()) || p.startsWith(guard.realPath(r))));

    }

    private static PermissionEntry find(WorkspaceGuard guard, Path path, ToolPermissionContext context, PermissionBehavior behavior, boolean write) {
        for (PermissionEntry entry : context.permissionEntries()) {
            if (entry.behavior() == behavior && matches(guard, path, entry, write)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean matches(WorkspaceGuard guard, Path path, PermissionEntry entry, boolean write) {
        String pattern = entry.rule().argumentPattern();

        if (!StringUtils.hasText(pattern) || !entry.rule().appliesTo(write ? "Edit" : "Read")) {
            return false;
        }
        if (entry.behavior() == PermissionBehavior.ALLOW) {
            return PermissionPathMatcher.matches(guard.getRoot(), path, pattern);
        }
        return guard.pathsForCheck(path).stream().anyMatch(p -> PermissionPathMatcher.matches(guard.getRoot(), p, pattern));
    }
}
