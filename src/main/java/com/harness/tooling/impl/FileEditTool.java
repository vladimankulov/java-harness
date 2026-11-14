package com.harness.tooling.impl;

import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.tooling.Tool;
import com.harness.tooling.guard.WorkspaceGuard;
import com.harness.tooling.guard.WorkspaceViolationException;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileEditTool implements Tool<FileEditTool.Input> {

    private final WorkspaceGuard workspaceGuard;

    public FileEditTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    @Override
    public String name() {
        return "Edit";
    }

    @Override
    public String description() {
        return """
                Replace exact piece of text in a file.
                
                Usage:
                - You MUST use Read tool on a file first. This tool if you haven't read the file first.
                - oldString must match exactly, whitespace included. Use Read to see the exact text.
                - The edit FAILS if oldString is found more than once. Either provide a larger string
                to more surrounding context to make it unique, or set replaceAll to change every occurrence.
                - oldString and newString must differ.
                """;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String renderToolUsage(Input input) {
        return StringUtils.hasText(input.filePath) ? "Edit: %s".formatted(input.filePath) : "Edit";
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (!StringUtils.hasText(input.filePath)) {
            return ToolResult.error("File path isn't specified");
        }
        if (!StringUtils.hasText(input.oldString)) {
            return ToolResult.error("Old string isn't specified");
        }
        if (!StringUtils.hasText(input.newString)) {
            return ToolResult.error("New string isn't specified");
        }
        if (input.oldString.equals(input.newString)) {
            return ToolResult.error("Old string and new strings are equal. Nothing to change");
        }

        Path toFile;

        try {
            toFile = workspaceGuard.resolve(input.filePath);
        } catch (WorkspaceViolationException e) {
            return ToolResult.error(e.getMessage());
        }

        if (!Files.exists(toFile)) {
            return ToolResult.error("Requested file '%s' does not exists".formatted(input.filePath));
        }
        if (Files.isDirectory(toFile)) {
            return ToolResult.error("Requested file '%s' is directory".formatted(input.filePath));
        }
        String refusal = context.fileStateGuard().whyCannotWrite(toFile);
        if (refusal != null) {
            return ToolResult.error("Cannot edit file '%s': %s".formatted(input.filePath, refusal));
        }

        String before;

        try {
            before = Files.readString(toFile);
        } catch (IOException e) {
            return ToolResult.error(
                    "Failed to read the file: '%s', err: %s".formatted(input.filePath, e.getMessage()));
        }

        int occurrences = StringUtils.countOccurrencesOf(before, input.oldString);

        if (occurrences == 0) {
            return ToolResult.error("Old string not found in the file '%s'. Use Read tool to reread the file".formatted(input.filePath));
        }

        if (occurrences > 1 && !Boolean.TRUE.equals(input.replaceAll)) {
            return ToolResult.error(
                    "Old string found %d times in the file '%s'. Either provide a larger string to more surrounding context to make it unique, or set replaceAll to change every occurrence."
                            .formatted(occurrences, input.filePath));
        }
        String after = before.replace(input.oldString, input.newString);
        try {
            Files.writeString(toFile, after);
        } catch (IOException e) {
            return ToolResult.error(
                    "Failed to write the file: '%s', err: %s".formatted(input.filePath, e.getMessage()));
        }
        context.fileStateGuard().remember(toFile);
        return ToolResult.ok(
                "Replaced %d occurrences of old string in the file '%s'".formatted(occurrences,
                        input.filePath));
    }

    public record Input(
            @ToolParam(description = "Path to the file to edit, relative to the project root")
            String filePath,
            @ToolParam(description = "Exact text to replace, whitespace included")
            String oldString,
            @ToolParam(description = "New text to replace with, whitespace included")
            String newString,
            @ToolParam(required = false, description = "Replace every occurrence instead of failing on duplicates")
            Boolean replaceAll
    ) {

    }
}
