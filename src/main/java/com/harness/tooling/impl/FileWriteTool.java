package com.harness.tooling.impl;

import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.tooling.Tool;
import com.harness.tooling.guard.WorkspaceGuard;
import com.harness.tooling.guard.WorkspaceViolationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileWriteTool implements Tool<FileWriteTool.Input> {

  private final WorkspaceGuard workspaceGuard;

  public FileWriteTool(WorkspaceGuard workspaceGuard) {
    this.workspaceGuard = workspaceGuard;
  }

  @Override
  public String name() {
    return "Write";
  }

  @Override
  public String description() {
    return """
        Write a file to the local filesystem.
        
        Usage:
        - Use this tool to create a new file or to rewrite one completely.
        - If file exists you must Read it first. This tool will fail it you haven't read the file first.
        """;
  }

  @Override
  public Class<FileWriteTool.Input> inputType() {
    return Input.class;
  }

  @Override
  public ToolResult call(FileWriteTool.Input input, ToolContext context) {
    if (!StringUtils.hasText(input.filePath)) {
      return ToolResult.error("File path isn't specified");
    }
    if (!StringUtils.hasText(input.content)) {
      return ToolResult.error("Content isn't specified");
    }

    Path toFile;

    try {
      toFile = workspaceGuard.resolve(input.filePath);
    } catch (WorkspaceViolationException e) {
      return ToolResult.error(e.getMessage());
    }

    if (Files.isDirectory(toFile)) {
      return ToolResult.error("Requested file '%s' is directory".formatted(input.filePath));
    }

    String denialReason = context.fileStateGuard().whyCannotWrite(toFile);

    if (Files.exists(toFile) && denialReason != null) {
      return ToolResult.error(
          "Cannot write to the file '%s': %s".formatted(input.filePath, denialReason));
    }

    try {
      Path parrent = toFile.getParent();
      if (parrent != null && !Files.exists(parrent)) {
        Files.createDirectories(parrent);
      }
      Files.writeString(toFile, input.content);
    } catch (IOException e) {
      return ToolResult.error(
          "Failed to write the file: '%s', err: %s".formatted(input.filePath, e.getMessage()));
    }
    context.fileStateGuard().remember(toFile);
    return ToolResult.ok("Wrote content to the file '%s'".formatted(input.filePath));
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String renderToolUsage(FileWriteTool.Input input) {
    return Tool.super.renderToolUsage(input);
  }

  public record Input(
      @ToolParam(description = "Path to the file to edit, relative to the project root")
      String filePath,
      @ToolParam(description = "Full new content of the file")
      String content
  ) {

  }
}
