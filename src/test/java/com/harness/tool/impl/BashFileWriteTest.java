package com.harness.tool.impl;

import com.harness.agent.property.AgentProperty;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.tooling.ToolResult;
import com.harness.permission.PermissionPrompt;
import com.harness.tooling.ToolRegistry;
import com.harness.tooling.guard.FileStateGuard;
import com.harness.tooling.guard.WorkspaceGuard;
import com.harness.tooling.impl.BashTool;
import fixtures.TestPermissionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Текст в файл пишет Write: Bash такую команду отклоняет до вопроса человеку.
 */
class BashFileWriteTest {

    @TempDir
    Path workspace;

    @ParameterizedTest
    @ValueSource(strings = {
            "echo LESSON7_EDITS > target/lesson7-note.txt",
            "echo \"LESSON7_EDITS\" > \"C:\\Users\\me\\project\\target\\lesson7-note.txt\"",
            "echo hi >> notes.txt",
            "@echo off > run.cmd",
            "type nul > empty.txt",
            "cat <<EOF > notes.md\nhello\nEOF",
            "printf 'a\\nb' > out.txt",
            "mkdir -p target && echo x > target/a.txt",
            "Set-Content -Path notes.txt -Value 'Hasta la vista'",
            "'hello' | Out-File notes.txt",
            "New-Item -Path notes.txt -ItemType File -Value hi",
            "echo hi | tee notes.txt",
            "touch notes.txt",
            "Write-Output 'x' > a.txt"})
    void commandThatWritesTextIntoFileIsRecognized(String command) {
        assertThat(BashTool.authoredFileTarget(command)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".\\mvnw.cmd test",
            ".\\mvnw.cmd test > build.log 2>&1",
            "mkdir build && ./mvnw test",
            "New-Item -ItemType Directory notes",
            "ping -n 20 127.0.0.1 > nul",
            "echo ok > /dev/null",
            "echo %JAVA_HOME%",
            "echo \"a > b\"",
            "git log --output=result.txt",
            "git status && echo hello",
            "dir target",
            "cat pom.xml"})
    void otherCommandsAreLeftAlone(String command) {
        assertThat(BashTool.authoredFileTarget(command)).isNull();
    }

    @Test
    void writingCommandIsRejectedWithoutAskingTheUserAndPointsToWrite() {
        AtomicInteger questions = new AtomicInteger();
        BashTool bash = new BashTool(new WorkspaceGuard(new AgentProperty(20, workspace.toString(), true)));
        ToolRegistry registry = TestPermissionConfig.registry(
                List.of(bash), event -> {
                }, new FileStateGuard(),
                TestPermissionConfig.properties(PermissionMode.DEFAULT, List.of(), List.of(), List.of()),
                (tool, target, reason) -> {
                    questions.incrementAndGet();
                    return PermissionPrompt.Answer.ALLOW_ONCE;
                });

        ToolResult result = registry.execute("Bash", "{\"command\": \"echo hi > notes.txt\"}");

        assertThat(result.error()).isTrue();
        assertThat(result.content()).startsWith("Call Write tool").contains("notes.txt");
        assertThat(questions).hasValue(0);
        assertThat(workspace.resolve("notes.txt")).doesNotExist();
    }

    @Test
    void existingFileIsSentToReadThenEdit() throws Exception {
        java.nio.file.Files.writeString(workspace.resolve("notes.txt"), "old\n");
        BashTool bash = new BashTool(new WorkspaceGuard(new AgentProperty(20, workspace.toString(), true)));

        String message = bash.validateInput(new BashTool.Input("echo new >> notes.txt", null, null),
                TestPermissionConfig.context(new FileStateGuard()));

        assertThat(message).startsWith("Call Read").contains("Edit");
        assertThat(workspace.resolve("notes.txt")).hasContent("old");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mkdir -p notes/today", "md notes", "mkdir build && ./mvnw test",
            "New-Item -ItemType Directory -Path notes", "cd src && mkdir x"})
    void directoryCreationIsRecognized(String command) {
        assertThat(BashTool.makesDirectory(command)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {".\\mvnw.cmd test", "echo mkdir", "git status", "New-Item -Path a.txt"})
    void otherCommandsDoNotMakeDirectories(String command) {
        assertThat(BashTool.makesDirectory(command)).isFalse();
    }

    @Test
    void listingInsideProjectIsReadWithoutQuestion() {
        BashTool bash = new BashTool(new WorkspaceGuard(new AgentProperty(20, workspace.toString(), true)));
        var context = TestPermissionConfig.context(new FileStateGuard());
        assertThat(BashTool.listedPath("dir target")).isEqualTo("target");
        assertThat(BashTool.listedPath("ls -la src/main")).isEqualTo("src/main");
        assertThat(BashTool.listedPath("dir target && del x")).isNull();
        assertThat(bash.checkPermission(new BashTool.Input("dir target", null, null), context).behavior())
                .isEqualTo(PermissionBehavior.ALLOW);
        //windows dependent paths are not working inside macos because they are resolved to the root
//        assertThat(bash.checkPermission(new BashTool.Input("dir C:\\Windows", null, null), context).behavior())
//                .isNotEqualTo(PermissionBehavior.ALLOW);
        assertThat(bash.checkPermission(new BashTool.Input("dir /Users", null, null), context).behavior())
                .isNotEqualTo(PermissionBehavior.ALLOW);
    }
}
