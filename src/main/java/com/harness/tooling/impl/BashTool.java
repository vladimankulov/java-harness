package com.harness.tooling.impl;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.permission.CommandInspector;
import com.harness.tooling.Tool;
import com.harness.tooling.guard.WorkspaceGuard;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class BashTool implements Tool<BashTool.Input> {

    private final WorkspaceGuard workspaceGuard;

    public static final int MAX_OUTPUT_CHARACTERS = 8000;
    private static final int DEFAULT_TIMEOUT_PROCESS = 120;//seconds
    private static final int MAX_TIMEOUT_PROCESS = 600; //seconds
    private static final int MIN_TIMEOUT_PROCESS = 1; //seconds

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
                - Use this to build and run tests. On Windows commands run in cmd.exe otherwise /bin/bash.
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
        return "[%s] %s".formatted(name(), input.command);
    }

    @Override
    public String inputSchema() {
        return Tool.super.inputSchema();
    }

    @Override
    public PermissionDecision checkPermission(Input input, ToolContext context) {
        return CommandInspector.check(input.command, context.permissions());
    }

    public int getMaxOutputCharacters() {
        return MAX_OUTPUT_CHARACTERS;
    }

    public record Input(
            @ToolParam(description = "Shell command to execute")
            String command,
            @ToolParam(required = false, description = "What this command does")
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
//        provideJdk(builder.environment());
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
}
