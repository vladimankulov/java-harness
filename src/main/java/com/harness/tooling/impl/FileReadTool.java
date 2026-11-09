package com.harness.tooling.impl;

import com.harness.model.tooling.ToolResult;
import com.harness.tooling.Tool;
import com.harness.tooling.guard.WorkSpaceGuard;
import com.harness.tooling.guard.WorkspaceViolationException;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class FileReadTool implements Tool<FileReadTool.Input> {

    private final static int MAX_LINE_LENGTH = 2000;
    private final static int DEFAULT_LINES_TO_READ = 2000;

    private final WorkSpaceGuard workSpaceGuard;

    public FileReadTool(WorkSpaceGuard workSpaceGuard) {
        this.workSpaceGuard = workSpaceGuard;
    }

    @Override
    public String name() {
        return "Read";
    }

    @Override
    public String description() {
        return """
                Read a file from the local filesystem. Use this instead of guessing file contents.
                
                Usage:
                - file_path is relative to the project root.
                - Output is in the `cat -n` format: line number, tab, then the line content
                - Reads up to %d content; use offset and limit for the larger files
                - Return the error message if the file does not exist
                """.formatted(DEFAULT_LINES_TO_READ);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public ToolResult call(Input input) {
        Path toFile;
        try {
            toFile = workSpaceGuard.resolve(input.filePath);
        } catch (WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }
        if (!Files.exists(toFile)) {
            return ToolResult.error("Requested file '%s' does not exists".formatted(input.filePath));
        }
        if (Files.isDirectory(toFile)) {
            return ToolResult.error("Requested file '%s' is directory".formatted(input.filePath));
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(toFile);
        }
//        catch (MalformedInputException e) {
//            return ToolResult.error("Requested file '%s' is not readable, err: %s".formatted(input.filePath, e.getMessage()));
//        }
        catch (IOException e) {
            return ToolResult.error("Failed to read the file: '%s', err: %s".formatted(input.filePath, e.getMessage()));
        }

        if (CollectionUtils.isEmpty(lines)) {
            return ToolResult.ok("");
        }

        int from = Math.max(1, input.offset == null ? 1 : input.offset);
        int to = input.limit == null ? 1 : Math.max(1, input.limit);

        if (from > lines.size()) {
            return ToolResult.error("Requested file contains: %d, you asked beyond limit".formatted(lines.size()));
        }
        String content = render(lines, from, to);
        return ToolResult.ok(content);
    }

    @Override
    public String renderToolUsage(Input input) {
        return input.toString();
    }

    public record Input(
            @ToolParam(description = "Path to file, relative to the project root")
            String filePath,
            @ToolParam(required = false, description = "1-based number line to start from")
            Integer offset,
            @ToolParam(required = false, description = "How many lines to read, default 2000")
            Integer limit
    ) {
    }

    private String render(List<String> lines, int from, int to) {
        StringBuilder builder = new StringBuilder();

        for (int i = from; i <= to; i++) {
            String line = lines.get(i - 1);//llm request 1-based lines
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH) + "(line is truncated)";
            }
            builder.append(i).append("\t").append(line).append("\n");
        }
        if (to < lines.size()) {
            builder.append("... you've read file line starting from %d-%d from a file length: %d, use offset to continue reading file".formatted(from, to, lines.size()));
        }

        return builder.toString();
    }
}
