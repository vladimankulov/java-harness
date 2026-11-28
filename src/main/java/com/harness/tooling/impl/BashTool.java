package com.harness.tooling.impl;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.permission.inspectors.BashInspector;
import com.harness.permission.inspectors.PathInspector;
import com.harness.tooling.Tool;
import com.harness.tooling.guard.WorkspaceGuard;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BashTool implements Tool<BashTool.Input> {

    private final WorkspaceGuard workspaceGuard;

    public static final int MAX_OUTPUT_CHARACTERS = 8000;
    private static final int DEFAULT_TIMEOUT_PROCESS = 120;//seconds
    private static final int MAX_TIMEOUT_PROCESS = 600; //seconds
    private static final int MIN_TIMEOUT_PROCESS = 1; //seconds
    private static final Pattern GIT_BRANCH_QUESTION = Pattern.compile(
            "(?i)^\\s*git\\s+(?:branch(?:\\s+--show-current)?|rev-parse\\s+--abbrev-ref\\s+HEAD|symbolic-ref\\s+(?:--short\\s+)?HEAD)\\s*$");
    private static final Pattern TEXT_REDIRECT = Pattern.compile(
            "(?i)^\\s*[@(]?\\s*(?:echo|printf|cat|type|write-output|write-host)\\b.*?(?<![0-9&])>>?(?!&)\\s*(\"[^\"]*\"|'[^']*'|[^\\s<>()]+)");
    private static final Pattern WRITING_COMMAND = Pattern.compile(
            "(?i)^\\s*(?:set-content|add-content|out-file|tee-object|tee|touch|copy\\s+con|fsutil\\s+file\\s+createnew|new-item(?!.*-itemtype\\s+[\"']?directory))\\b"
                    + "(?:\\s+-(?:path|filepath|literalpath|a|append))*\\s*(\"[^\"]*\"|'[^']*'|[^\\s<>()-][^\\s<>()]*)?");
    private static final List<String> NOWHERE = List.of("nul", "nul:", "$null", "/dev/null");
    private static final Pattern MAKE_DIRECTORY = Pattern.compile(
            "(?i)^\\s*(?:mkdir|md|new-item(?=.*-itemtype\\s+[\"']?directory))\\b");
    private static final Pattern LISTING = Pattern.compile(
            "(?i)^\\s*(?:dir|ls)(?:\\s+(?:--?[a-z]+|/[a-z]+))*\\s+(\"[^\"]+\"|[^\\s\"]+)\\s*$");

    public BashTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return """
                Executes a shell command in the working directory and returns its exit code and output.
                - Use this to build and run tests. On Mac OS/Linux  platforms commands run /bin/bash other commands run in cmd.exe.
                - Prefer Grep, Edit, Write, Read, Glob tools for inspecting and editing files.
                - Only the last %d output characters are retained
                - A command might require a user's conformation or be refused
                """.formatted(MAX_OUTPUT_CHARACTERS);
    }

    @Override
    public Class<Input> inputType() {
        return BashTool.Input.class;
    }

    @Override
    public @Nullable String validateInput(Input input, ToolContext context) {
        if (asksForBranch(input.command())) {
            return """
                    Current branch exists in the `# Repository` system prompt section,
                    in the same section you can find changed files at the start of the conversation.
                    Give response based on this information.
                    Bash doesn't show current branch, command hasn't been executed.
                    """;
        }
        String target = authoredFileTarget(input.command());

        if (target == null) {
            if (!makesDirectory(input.command())) return null;
            return """
                    Call Write tool for a file you are trying to put in the current directory. Missing directories Write tool will create itself.
                    If you need a clean directory - use Write tool to create an empty .gitkeep file.
                    Bash doesn't create directories, command hasn't been executed.
                    """;
        }
        if (exists(target)) {
            return """
                    Call Read tool for the \\"%s\\", then Edit - to change the file or Write to rewrite it completely.
                    Bash doesn't write text into files, command hasn't been executed
                    """.formatted(target);
        }
        return """
                Call Write tool: filePath:\\"%s\\", content: file's text. Missing directories Write tool will create itself.
                Bash doesn't write text into files, command hasn't been executed.
                """.formatted(target);
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (!StringUtils.hasText(input.command)) {
            return ToolResult.error("command parameter is empty, provide it");
        }
        int timeout = input.timeoutSeconds == null ? DEFAULT_TIMEOUT_PROCESS : input.timeoutSeconds;
        if (timeout < MIN_TIMEOUT_PROCESS || timeout > MAX_TIMEOUT_PROCESS) {
            return ToolResult.error("timeout seconds must be within %s-%s, provide a valid timeout value".formatted(MIN_TIMEOUT_PROCESS, MAX_TIMEOUT_PROCESS));
        }

        Path script = null;
        Process process = null;

        try {
            script = writeScript(input.command);
            process = startProcess(script);
            CompletableFuture<String> result = readOutputInBackground(process);
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                return ToolResult.error("Command %s wasn't finished within a timeout %d and was interrupted".formatted(input.command, timeout));
            }
            return report(process.exitValue(), result.get(5, TimeUnit.SECONDS));
        } catch (IOException e) {
            return ToolResult.error("Failed to execute command: %s, err: %s".formatted(input.command, e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("command: %s was interrupted".formatted(input.command));
        } catch (ExecutionException | TimeoutException e) {
            return ToolResult.error("Failed to read the output of the command: %s, err: %s".formatted(input.command, e.getMessage()));
        } finally {
            cleanup(script, process);
        }
    }

    private ToolResult report(int exitCode, String content) {
        return ToolResult.ok("exitCode: %s\n%s".formatted(exitCode, StringUtils.hasText(content) ? content : "empty output"));
    }

    private void cleanup(@Nullable Path script, @Nullable Process process) {
        if (script != null) {
            try {
                Files.delete(script);
            } catch (IOException ignored) {

            }
        }
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private CompletableFuture<String> readOutputInBackground(Process process) {
        return CompletableFuture.supplyAsync(() -> readTail(process.getInputStream()));
    }

    private String readTail(InputStream inputStream) {
        StringBuilder tail = new StringBuilder();

        boolean truncated = false;

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                tail.append(buffer, 0, read);
                if (tail.length() > MAX_OUTPUT_CHARACTERS) {
                    tail.delete(0, tail.length() - MAX_OUTPUT_CHARACTERS);
                    truncated = true;
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return truncated ? tail.toString() + "..(truncated)" : tail.toString();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String renderToolUsage(Input input) {
        return StringUtils.hasText(input.command) ? input.command : "";
    }

    @Override
    public String inputSchema() {
        return Tool.super.inputSchema();
    }

    @Override
    public PermissionDecision checkPermission(Input input, ToolContext context) {
        PermissionDecision decision = BashInspector.check(input.command, context.permissions());
        String listed = listedPath(input.command());
        if (listed == null || decision.behavior() != PermissionBehavior.ASK || decision.askAlways()) return decision;
        PermissionDecision permissionDecision = PathInspector.checkRead(workspaceGuard, listed, context.permissions());
        return permissionDecision;
    }

    public int getMaxOutputCharacters() {
        return MAX_OUTPUT_CHARACTERS;
    }

    public record Input(
            @ToolParam(description = "Shell command to execute")
            String command,
            @ToolParam(required = false, description = "What this command does")
            @Nullable
            String description,
            @ToolParam(required = false, description = "Timeout in seconds, default 120, max is 600")
            @Nullable
            Integer timeoutSeconds
    ) {
    }


    private Process startProcess(Path script) throws IOException {
        List<String> command = isWindows() ? List.of("cmd.exe", "/d", "/c", script.toString()) : List.of("/bin/sh", script.toString());
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workspaceGuard.getRoot().toFile())
                .redirectErrorStream(true);
        provideJdk(builder.environment());
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private static Path writeScript(String command) throws IOException {
        Path script = Files.createTempFile("harness-", isWindows() ? ".cmd" : ".sh");
        String scriptContent = isWindows() ?
                "@echo off\r\nchcp 65001 > nul\r\n" + command + "\r\n" :
                "#!/bin/sh\n" + command + "\n";
        Files.writeString(script, scriptContent, StandardCharsets.UTF_8);
        return script;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void provideJdk(Map<String, String> environment) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) return;
        environment.putIfAbsent("JAVA_HOME", javaHome);
        // В Windows переменная может называться Path, а не PATH.
        String pathKey = environment.keySet().stream().filter(key -> key.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
        String javaBin = Path.of(javaHome, "bin").toString();
        String path = environment.getOrDefault(pathKey, "");
        if (!path.contains(javaBin)) environment.put(pathKey, javaBin + File.pathSeparator + path);
    }

    public static boolean asksForBranch(@Nullable String command) {
        if (command == null) return false;
        for (String part : command.split("&&|\\|\\||[;|\\r\\n]")) {
            if (GIT_BRANCH_QUESTION.matcher(part).matches()) return true;
        }
        return false;
    }

    public static boolean makesDirectory(@Nullable String command) {
        if (command == null) return false;
        Matcher segments = Pattern.compile("[^;|&\\r\\n]+").matcher(maskQuoted(command));
        while (segments.find()) {
            if (MAKE_DIRECTORY.matcher(segments.group()).find()) return true;
        }
        return false;
    }

    public static @Nullable String listedPath(@Nullable String command) {
        if (command == null) return null;
        Matcher m = LISTING.matcher(command);
        return m.matches() ? m.group(1).replaceAll("^\"|\"$", "") : null;
    }

    public static @Nullable String authoredFileTarget(@Nullable String command) {
        if (command == null) return null;
        String masked = maskQuoted(command);
        Matcher segments = Pattern.compile("[^;|&\\r\\n]+").matcher(masked);
        while (segments.find()) {
            for (Pattern pattern : List.of(TEXT_REDIRECT, WRITING_COMMAND)) {
                Matcher m = pattern.matcher(masked).region(segments.start(), segments.end()).useAnchoringBounds(true);
                if (!m.find()) continue;
                String target = m.group(1) == null ? "" : command.substring(m.start(1), m.end(1)).replaceAll("^[\"']|[\"']$", "");
                if (NOWHERE.contains(target.toLowerCase(Locale.ROOT))) continue;
                return target.isEmpty() ? "путь к файлу" : target;
            }
        }
        return null;
    }

    private static String maskQuoted(String command) {
        StringBuilder out = new StringBuilder(command.length());
        char quote = 0;
        for (char c : command.toCharArray()) {
            if (quote != 0) {
                out.append(c == quote ? c : '_');
                if (c == quote) quote = 0;
            } else {
                if (c == '"' || c == '\'') quote = c;
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean exists(String target) {
        try {
            return Files.exists(workspaceGuard.resolve(target));
        } catch (RuntimeException unresolvable) {
            return false;
        }
    }
}
