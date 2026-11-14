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
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

@Component
public class GrepTool implements Tool<GrepTool.Input> {

    private static final int MAX_MATCHES = 100;
    private final WorkspaceGuard workspaceGuard;

    public GrepTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Grep";
    }

    @Override
    public String description() {
        return """
                Searches inside files with a regular expression. Use this when you know
                that the code says, but not which file says it.
                
                Usage:
                - Output modes: FILES_WITH_MATHCES lists path(default), CONTENT shows matching lines with
                line numbers, COUNT shows how many matches per file
                - Narrow the search with glob, for example **/*.java
                - Prefer FILES_WITH_MATCHES first, then read the interesting file with Read
                - Returns at most %d matches
                """.formatted(MAX_MATCHES);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (!StringUtils.hasText(input.pattern)) {
            return ToolResult.error("Pattern not specified!");
        }
        int flags = Boolean.TRUE.equals(input.ignoreCase) ? Pattern.CASE_INSENSITIVE : 0;

        Pattern regex;
        try {
            regex = Pattern.compile(input.pattern, flags);
        } catch (PatternSyntaxException e) {
            return ToolResult.error("Invalid pattern: %s, err: %s".formatted(input.pattern, e.getMessage()));
        }

        Path root;
        try {
            root = !StringUtils.hasText(input.path) ? workspaceGuard.getRoot() : workspaceGuard.resolve(input.path);
        } catch (WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }

        if (!Files.exists(root)) {
            return ToolResult.error("Path: %s not found".formatted(input.path));
        }

        if (!Files.isDirectory(root)) {
            return ToolResult.error("Failed to traverse the directory: %s".formatted(input.path));
        }

        PathMatcher globMatcher = StringUtils.hasText(input.glob) ? GlobTool.matcherFor(input.glob) : null;

        List<Path> files;

        try (Stream<Path> walkedFiles = Files.walk(root)) {
            files = walkedFiles
                    .filter(file -> !Ignored.contains(root, file))
                    .filter(file -> globMatcher == null || globMatcher.matches(root.relativize(file)))
                    .toList();
        } catch (IOException e) {
            return ToolResult.error("Failed to traverse directory: %s, err: %s".formatted(root, e.getMessage()));
        }
        OutputMode outputMode = Objects.requireNonNullElse(input.outputMode, OutputMode.FILES_WITH_MATCHES);

        return switch (outputMode) {
            case FILES_WITH_MATCHES -> renderFiles(files, regex, input.pattern());
            case CONTENT -> renderContent(files, regex, input.pattern());
            case COUNT -> renderCounts(files, regex, input.pattern());
        };
    }

    private ToolResult renderFiles(List<Path> files, Pattern regex, String pattern) {
        List<String> matches = new ArrayList<>();

        for (Path file : files) {
            if (fileContentMatchesRegex(file, regex)) {
                matches.add(workspaceGuard.relativize(file));
            }
            if (matches.size() >= MAX_MATCHES) {
                break;
            }
        }

        if (CollectionUtils.isEmpty(matches)) {
            return nothingFound(pattern);
        }

        matches.add("You have shown only the file paths. If you need to read the content of the file, use Read tool");

        String content = String.join("\n", matches);

        return ToolResult.ok(content);
    }

    private ToolResult renderContent(List<Path> files, Pattern regex, String pattern) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;

        for (Path file : files) {
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size() && shown < MAX_MATCHES; i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    builder.append(workspaceGuard.relativize(file));
                    builder.append(" ");
                    builder.append(i+1).append("\t").append(lines.get(i).strip()).append("\n");
                    shown += 1;
                }
            }
            if (shown >= MAX_MATCHES) {
                builder.append("...showed first %d matches, try narrow the pattern %s".formatted(shown, pattern));
                break;
            }
        }
        return shown == 0 ? nothingFound(pattern) : ToolResult.ok(builder.toString());
    }

    private ToolResult renderCounts(List<Path> files, Pattern regex, String pattern) {
        List<String> matches = new ArrayList<>();

        long total = 0;
        for (Path file : files) {
            long count = readLines(file).stream().filter(line -> regex.matcher(line).find())
                    .count();

            if (count > 0) {
                matches.add("%s: %d".formatted(file, count));
                total += count;
            }
        }

        if (CollectionUtils.isEmpty(matches)) {
            return nothingFound(pattern);
        }

        matches.add("Total hints: %s found in %d files".formatted(total, matches.size()));

        String content = String.join("\n", matches);
        return ToolResult.ok(content);
    }

    private ToolResult nothingFound(String regex) {
        return ToolResult.ok("Nothing is found by regex: %s".formatted(regex));
    }

    private boolean fileContentMatchesRegex(Path file, Pattern regex) {
        return readLines(file).stream().anyMatch(line -> regex.matcher(line).find());
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (MalformedInputException e) {
            return List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    public record Input(
            @ToolParam(description = "Regular expression to search for")
            String pattern,
            @ToolParam(required = false, description = "Directory to search in, default to the project root")
            String path,
            @ToolParam(required = false, description = "Only search files matching this glob, for example **/*.java")
            String glob,
            @ToolParam(required = false, description = "FILES_WITH_MATHCES lists path(default), CONTENT shows matching lines with\n" +
                    "line numbers, COUNT shows how many matches per file")
            OutputMode outputMode,
            @ToolParam(required = false, description = "Case insensitive search")
            Boolean ignoreCase

    ) {
    }

    public enum OutputMode {
        FILES_WITH_MATCHES,
        CONTENT,
        COUNT
    }
}
