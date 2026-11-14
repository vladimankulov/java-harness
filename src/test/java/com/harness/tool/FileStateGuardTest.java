package com.harness.tool;

import com.harness.tooling.guard.FileStateGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStateGuardTest {

    @TempDir
    Path workspace;

    private final FileStateGuard cache = new FileStateGuard();

    @Test
    void новыйФайлПисатьМожно() {
        assertThat(cache.whyCannotWrite(workspace.resolve("ещё-нет.java"))).isNull();
    }

    @Test
    void непрочитанныйФайлПисатьНельзя() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");

        assertThat(cache.whyCannotWrite(file)).contains("hasn't been read yet", "Read");
    }

    @Test
    void прочитанныйФайлПисатьМожно() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");

        cache.remember(file);

        assertThat(cache.whyCannotWrite(file)).isNull();
    }

    @Test
    void изменившийсяФайлПисатьНельзя() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");
        cache.remember(file);

        Files.writeString(file, "class Agent { void run() {} }\n");
        touchLater(file);

        assertThat(cache.whyCannotWrite(file)).contains("has changed", "Read");
    }

    /**
     * Отметка времени меняется без изменения содержимого — так делают облачная
     * синхронизация, антивирус и сохранение в IDE без правок. Текст тот же,
     * значит представление модели не устарело, и запрещать нечего.
     */
    @Test
    void новаяОтметкаВремениПриТомЖеТекстеНеМешает() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");
        cache.remember(file);

        touchLater(file);

        assertThat(cache.whyCannotWrite(file)).isNull();
    }

    @Test
    void путьНормализуется() throws IOException {
        Path file = Files.writeString(workspace.resolve("Agent.java"), "class Agent {}\n");
        cache.remember(file);

        Path окольныйПуть = workspace.resolve("пусто").resolve("..").resolve("Agent.java");

        assertThat(cache.whyCannotWrite(окольныйПуть)).isNull();
    }

    private static void touchLater(Path file) throws IOException {
        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(file).toMillis() + 5_000));
    }
}
