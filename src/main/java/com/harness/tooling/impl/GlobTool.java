package com.harness.tooling.impl;

import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.tooling.Tool;
import com.harness.tooling.guard.Ignored;
import com.harness.tooling.guard.WorkspaceGuard;
import com.harness.tooling.guard.WorkspaceViolationException;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class GlobTool implements Tool<GlobTool.Input> {

    private final static int MAX_RESULTS = 100;
    private final WorkspaceGuard workspaceGuard;

    public GlobTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Glob";
    }

    @Override
    public String description() {
        return """
                Finds files by name pattern. Use this tool only when you know the name of the file but not its path.
                
                Usage:
                - Supports patterns like "**/*.java" or "**/*Tool*.java"
                - Returns path relative to the project root, most recently modified first.
                - Returns at most %d results
                """.formatted(MAX_RESULTS);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (!StringUtils.hasText(input.pattern)) {
            return ToolResult.error("Pattern isn't specified");
        }
        Path root;
        try {
            root = !StringUtils.hasText(input.path()) ? workspaceGuard.getRoot() : workspaceGuard.resolve(input.path);
        } catch (WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }

        PathMatcher pathMatcher;
        try {
            pathMatcher = matcherFor(input.pattern);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Failed to read pattern syntax, err: %s".formatted(e.getMessage()));
        }

        if (!Files.exists(root)) {
            return ToolResult.error("Path: %s not found".formatted(input.path));
        }

        if (!Files.isDirectory(root)) {
            return ToolResult.error("Failed to traverse the directory: %s".formatted(input.path));
        }

        List<Path> files;

        try (Stream<Path> walkedFiles = Files.walk(root)) {
            files = walkedFiles
                    .filter(file -> !Ignored.contains(root, file))
                    .filter(file -> pathMatcher.matches(root.relativize(file)))
                    .sorted(Comparator.comparing(GlobTool::modifiedAt).reversed())
                    .limit(MAX_RESULTS + 1)
                    .toList();

        } catch (IOException e) {
            return ToolResult.error("Failed to find files, err: %s".formatted(e.getMessage()));
        }
        if (CollectionUtils.isEmpty(files)) {
            return ToolResult.ok("Files are not found by your pattern: %s".formatted(input.pattern()));
        }

        StringBuilder content = new StringBuilder();
        files.stream().limit(MAX_RESULTS).forEach(file -> content.append(workspaceGuard.relativize(file)).append("\n"));

        if (files.size() > MAX_RESULTS) {
            content.append("... showed first %d files, reduce pattern search".formatted(MAX_RESULTS));
        }

        return ToolResult.ok(content.toString());
    }

    private static long modifiedAt(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String renderToolUsage(Input input) {
        String pattern = StringUtils.hasText(input.pattern) ? input.pattern : "";
        return name() + ":" + pattern;
    }

    public static PathMatcher matcherFor(String pattern) {
        final String globPattern = "glob:";
        final String findAll = "**/";

        PathMatcher asWritten = FileSystems.getDefault().getPathMatcher(globPattern + pattern);
        if (!pattern.startsWith(findAll)) {
            return asWritten;
        } else {
            PathMatcher rootMatcher = FileSystems.getDefault().getPathMatcher(globPattern + pattern.substring(3));
            return path -> asWritten.matches(path) || rootMatcher.matches(path);
        }
    }

    public record Input(
            @ToolParam(description = "Glob pattern, for example: **/*.java or **/*Tool*.java")
            String pattern,
            @ToolParam(required = false, description = "Directory to search in, defaults to the project root")
            String path

    ) {
    }
}
