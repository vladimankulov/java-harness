package com.harness.permission.inspectors;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.permission.PermissionPathMatcher;
import com.harness.permission.PermissionUpdate;
import com.harness.tooling.guard.WorkspaceGuard;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Инспектирует один путь по правилам. Read/Grep/Glob вызывают checkRead; Edit/Write — checkWrite;
 * Bash проверяет здесь же пути своих аргументов.
 * Порядок: сетевой путь → DENY → явный ASK → защита настроек → рабочие каталоги и режим → ALLOW → обычный ASK.
 * Здесь работают только правила с путём: Read(src/**), Edit(.env). Правило на весь инструмент
 * (Read, Edit, Grep) проверяет PermissionEvaluator по имени инструмента.
 * Чтение подчиняется правилам Read, запись — правилам Edit; Write пишет по правилам Edit.
 * BYPASS здесь не учитываем: обычный ASK снимет PermissionEvaluator, а защиту настроек режим не снимает.
 * rawPath — путь из Input, например src/App.java; подпись "[EDIT] src/App.java" сюда не передаём.
 */
public final class PathInspector {
    private PathInspector() {
    }

    // Эти каталоги содержат настройки: обычного ALLOW и режима на запись в них недостаточно.
    private static final Set<String> PROTECTED_DIRECTORIES = Set.of(".git", ".idea", ".vscode", ".claude", ".harness", ".mvn", ".husky", ".cargo", ".devcontainer", ".yarn");
    // Эти файлы тоже считаем настройками, даже если они лежат вне служебного каталога.
    private static final Set<String> PROTECTED_FILES = Set.of(
            ".gitconfig", ".gitmodules", ".bashrc", ".bash_profile", ".bash_login", ".bash_aliases", ".bash_logout", ".zshrc", ".zprofile", ".zshenv", ".zlogin", ".zlogout",
            ".envrc", ".profile", ".npmrc", ".yarnrc", ".yarnrc.yml", ".pnp.cjs", ".pnp.loader.mjs", ".pnpmfile.cjs", "bunfig.toml", ".bunfig.toml", ".bazelrc",
            ".bazelversion", ".bazeliskrc", ".pre-commit-config.yaml", "lefthook.yml", "lefthook.yaml", ".lefthook.yml", ".lefthook.yaml", ".mcp.json", ".claude.json",
            ".ripgreprc", "pyrightconfig.json", "maven-wrapper.properties", "gradle-wrapper.properties");


    public static PermissionDecision checkRead(WorkspaceGuard guard, String rawPath, ToolPermissionContext settings) {
        return check(guard, rawPath, settings, false);
    }

    public static PermissionDecision checkWrite(WorkspaceGuard guard, String rawPath, ToolPermissionContext settings) {
        return check(guard, rawPath, settings, true);
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
            return PermissionDecision.allow("Access to the filePath %s allowed by the rule: %s".formatted(path, allow.rule().toString()));
        }

        return PermissionDecision.ask((write ? "changing file:" : "reading file:") + path + " outside of the working directories")
                .withSuggestions(suggestions(path, write, inside));


    }

    private static List<PermissionUpdate> suggestions(Path path, boolean write, boolean inside) {
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
                        if (PROTECTED_DIRECTORIES.contains(segment)) {
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

        List<Path> pathsToCheck = guard.pathsForCheck(path);
        return pathsToCheck
                .stream()
                .allMatch(pathToCheck -> roots.stream().anyMatch(rootPath -> pathToCheck.startsWith(rootPath.toAbsolutePath().normalize()) || pathToCheck.startsWith(guard.realPath(rootPath))));

    }

    private static @Nullable PermissionEntry find(WorkspaceGuard guard, Path path, ToolPermissionContext context, PermissionBehavior behavior, boolean write) {
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
