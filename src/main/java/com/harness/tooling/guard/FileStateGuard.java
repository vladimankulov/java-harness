package com.harness.tooling.guard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class FileStateGuard {

  private final Map<Path, FileState> fileStates = new ConcurrentHashMap<>();

  public void remember(Path filePath) {
    Path file = filePath.normalize();

    try {
      fileStates.put(file, new FileState(Files.readString(file), modifiedAt(file)));
    } catch (IOException e) {
      fileStates.remove(file);
    }
  }

  public @NullMarked @Nullable FileState get(Path filePath) {
    return fileStates.get(filePath.normalize());
  }

  public @NullMarked @Nullable String whyCannotWrite(Path filePath) {
    if (!Files.exists(filePath)) {
      return null;
    }

    Path file = filePath.normalize();
    FileState known = fileStates.get(file);

    if (known == null) {
      return "File hasn't been read yet, use Read tool first";
    }

    if (modifiedAt(file) > known.timestamp && !sameContentOnDisk(file, known)) {
      return "File content has changed since last read. Use Read tool to read the file content and repeat your changes";
    }
    return null;
  }

  private boolean sameContentOnDisk(Path filePath, FileState known) {
    try {
      String currentContent = Files.readString(filePath);
      return currentContent.equals(known.content);
    } catch (IOException e) {
      return false;
    }
  }

  private long modifiedAt(Path filePath) {
    try {
      return Files.getLastModifiedTime(filePath).toInstant().toEpochMilli();
    } catch (IOException e) {
      return 0L;
    }
  }


  public record FileState(
      String content,
      long timestamp
  ) {

  }
}
