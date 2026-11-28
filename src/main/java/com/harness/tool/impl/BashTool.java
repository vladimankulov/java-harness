package com.harness.tool.impl;

import com.harness.permission.CommandInspector;
import com.harness.permission.PathInspector;
import com.harness.permission.PermissionBehavior;
import com.harness.permission.PermissionDecision;
import com.harness.tool.Tool;
import com.harness.tool.ToolContext;
import com.harness.tool.ToolResult;
import com.harness.tool.WorkspaceGuard;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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

/**
 * Выполнение команды с ограничением времени и размера результата.
 */
@Component
public class BashTool implements Tool<BashTool.Input> {
    static final int MAX_OUTPUT_CHARS = 8_000;
    static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final WorkspaceGuard workspaceGuard;

    public BashTool(WorkspaceGuard workspaceGuard) {
        this.workspaceGuard = workspaceGuard;
    }

    public record Input(
            @ToolParam(description = "Shell command to execute")
            String command,

            @Nullable @ToolParam(required = false, description = "What this command does")
            String description,

            @Nullable
            @ToolParam(
                    required = false,
                    description = "Timeout in seconds, default 120, maximum 600")
            Integer timeoutSeconds) {
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return """
                Executes a shell command in the project directory and returns its exit code and output.
                - Use this to build and run tests. On Windows commands run in cmd.exe, otherwise /bin/sh.
                - Prefer Read, Grep, Glob and Edit for inspecting or editing files.
                - Only the last %d output characters are retained.
                - A command may require the user's confirmation or be refused."""
                .formatted(MAX_OUTPUT_CHARS);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Показываем человеку саму команду.
     */
    @Override
    public String renderToolUseMessage(Input input) {
        return input.command() == null ? "" : input.command();
    }

    /**
     * Текст в файл пишет Write, а не оболочка: так человек видит и разрешает саму запись, а не строку shell.
     * Отклоняем раньше вопроса человеку и говорим модели, что вызвать. Запуск программ с выводом в файл
     * (mvnw test > build.log), mkdir и вывод в nul сюда не относятся.
     *
     * <p>Вопрос «на какой мы ветке» тоже отклоняем: ответ уже лежит в разделе Repository системного промпта,
     * а с Bash под рукой модель упрямо идёт его перепроверять. git status пропускаем: после правок снимок
     * на начало разговора устаревает, и свежий статус бывает нужен по делу.
     */
    @Override
    public @Nullable String validateInput(Input input, ToolContext context) {
        if (asksForBranch(input.command())) {
            return "Текущая ветка уже есть в системном промпте, в разделе Repository, там же изменённые файлы "
                    + "на начало разговора. Ответь по нему. Bash ветку не показывает, команда не выполнялась.";
        }
        String target = authoredFileTarget(input.command());
        if (target == null) {
            if (!makesDirectory(input.command())) return null;
            return "Вызови Write для файла, который кладёшь в этот каталог: недостающие каталоги Write создаёт сам. "
                    + "Нужен пустой каталог — запиши в него Write пустой файл .gitkeep. "
                    + "Bash каталоги не создаёт, команда не выполнялась.";
        }
        if (exists(target)) {
            return ("Вызови Read для \"%s\", затем Edit — чтобы изменить файл, или Write — чтобы переписать его целиком. "
                    + "Bash текст в файлы не пишет, команда не выполнялась.").formatted(target);
        }
        return ("Вызови Write: filePath \"%s\", в content — весь текст файла. Недостающие каталоги Write создаст сам. "
                + "Bash текст в файлы не пишет, команда не выполнялась.").formatted(target);
    }

    private boolean exists(String target) {
        try {
            return Files.exists(workspaceGuard.resolve(target));
        } catch (RuntimeException unresolvable) {
            return false;
        }
    }

    /** «На какой мы ветке»: git branch, git branch --show-current, rev-parse и symbolic-ref для HEAD. */
    private static final Pattern BRANCH_QUESTION = Pattern.compile(
            "(?i)^\\s*git\\s+(?:branch(?:\\s+--show-current)?|rev-parse\\s+--abbrev-ref\\s+HEAD|symbolic-ref\\s+(?:--short\\s+)?HEAD)\\s*$");

    /** Хватит одной части составной команды: git branch && git status — тоже вопрос о ветке. */
    public static boolean asksForBranch(@Nullable String command) {
        if (command == null) return false;
        for (String part : command.split("&&|\\|\\||[;|\\r\\n]")) {
            if (BRANCH_QUESTION.matcher(part).matches()) return true;
        }
        return false;
    }

    /** Команды, которые выводят текст из самой командной строки или из файла: их вывод в файл — это запись. */
    private static final Pattern TEXT_REDIRECT = Pattern.compile(
            "(?i)^\\s*[@(]?\\s*(?:echo|printf|cat|type|write-output|write-host)\\b.*?(?<![0-9&])>>?(?!&)\\s*(\"[^\"]*\"|'[^']*'|[^\\s<>()]+)");
    /** Команды, чьё дело — положить текст в файл. New-Item каталога — это mkdir, его не трогаем. */
    private static final Pattern WRITING_COMMAND = Pattern.compile(
            "(?i)^\\s*(?:set-content|add-content|out-file|tee-object|tee|touch|copy\\s+con|fsutil\\s+file\\s+createnew|new-item(?!.*-itemtype\\s+[\"']?directory))\\b"
                    + "(?:\\s+-(?:path|filepath|literalpath|a|append))*\\s*(\"[^\"]*\"|'[^']*'|[^\\s<>()-][^\\s<>()]*)?");
    private static final List<String> NOWHERE = List.of("nul", "nul:", "$null", "/dev/null");
    /** Создание каталога: mkdir, md и New-Item с -ItemType Directory. */
    private static final Pattern MAKE_DIRECTORY = Pattern.compile(
            "(?i)^\\s*(?:mkdir|md|new-item(?=.*-itemtype\\s+[\"']?directory))\\b");
    /** Просмотр одного каталога: dir или ls с ключами и одним путём. */
    private static final Pattern LISTING = Pattern.compile(
            "(?i)^\\s*(?:dir|ls)(?:\\s+(?:--?[a-z]+|/[a-z]+))*\\s+(\"[^\"]+\"|[^\\s\"]+)\\s*$");

    static boolean makesDirectory(@Nullable String command) {
        if (command == null) return false;
        Matcher segments = Pattern.compile("[^;|&\\r\\n]+").matcher(maskQuoted(command));
        while (segments.find()) {
            if (MAKE_DIRECTORY.matcher(segments.group()).find()) return true;
        }
        return false;
    }

    /** Путь каталога, который команда только показывает; null — это не просмотр одного каталога. */
    static @Nullable String listedPath(@Nullable String command) {
        if (command == null) return null;
        Matcher m = LISTING.matcher(command);
        return m.matches() ? m.group(1).replaceAll("^\"|\"$", "") : null;
    }

    /**
     * Путь файла, в который команда пишет текст; null — команда файлов так не пишет. Текст в кавычках
     * маскируем, чтобы > и ; внутри строки не сошли за перенаправление и разделитель команд.
     */
    static @Nullable String authoredFileTarget(@Nullable String command) {
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

    /** Всё внутри кавычек заменяем на _, сами кавычки и длину строки сохраняем. */
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

    /** Сервис вызвал Tool. Tool передаёт проверку команды в CommandInspector. */
    @Override
    public PermissionDecision checkPermissions(Input input, ToolContext context) {
        PermissionDecision decision = CommandInspector.check(input.command(), context.permissions());
        // Просмотр каталога — это чтение: проверяем путь, как у Read и Glob. Правила Bash и обязательный вопрос важнее.
        String listed = listedPath(input.command());
        if (listed == null || decision.behavior() != PermissionBehavior.ASK || decision.askAlways()) return decision;
        return PathInspector.checkRead(workspaceGuard, listed, context.permissions());
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        if (input.command() == null || input.command().isBlank()) return ToolResult.error("не указана команда");
        int timeout = input.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : input.timeoutSeconds();
        if (timeout < 1 || timeout > 600) return ToolResult.error("таймаут должен быть от 1 до 600 секунд");
        Path script = null;
        Process process = null;
        try {
            script = writeScript(input.command());
            process = startProcess(script);
            CompletableFuture<String> output = readOutputInBackground(process);
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) return ToolResult.error("команда не уложилась в %d секунд и была прервана".formatted(timeout));
            return report(process.exitValue(), output.get(5, TimeUnit.SECONDS));
        } catch (IOException failure) {
            return ToolResult.error("не удалось запустить команду: " + failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ToolResult.error("выполнение прервано");
        } catch (ExecutionException | TimeoutException failure) {
            return ToolResult.error("не удалось прочитать вывод: " + failure.getMessage());
        } finally {
            cleanUp(process, script);
        }
    }

    private static ToolResult report(int exitCode, String output) {
        return ToolResult.ok("код возврата: %d%n%s".formatted(exitCode, output.isBlank() ? "(вывод пуст)" : output));
    }

    /**
     * Что бы ни случилось: процесс убит вместе с потомками, временный файл удалён.
     */
    private static void cleanUp(@Nullable Process process, @Nullable Path script) {
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        try {
            if (script != null) Files.deleteIfExists(script);
        } catch (IOException ignored) {
            // Ошибка уборки не подменяет результат команды.
        }
    }

    /**
     * Читаем вывод в отдельном потоке, пока основной ждёт завершения процесса. Без этого возможно
     * зависание: процесс заполнил буфер вывода и ждёт читателя, а мы ждём, когда он закончится.
     */
    private static CompletableFuture<String> readOutputInBackground(Process process) {
        return CompletableFuture.supplyAsync(() -> readTail(process.getInputStream()));
    }

    /**
     * Оставляем последние MAX_OUTPUT_CHARS символов: ошибка сборки и итог тестов печатаются в конце.
     */
    private static String readTail(InputStream stream) {
        StringBuilder tail = new StringBuilder();
        boolean truncated = false;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                tail.append(buffer, 0, read);
                if (tail.length() > MAX_OUTPUT_CHARS) {
                    tail.delete(0, tail.length() - MAX_OUTPUT_CHARS);
                    truncated = true;
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return (truncated ? "… (начало вывода обрезано)\n" : "") + tail;
    }



    /**
     * Команду пишем во временный файл: переданная аргументом, она доехала бы с испорченными кавычками.
     */
    private static Path writeScript(String command) throws IOException {
        Path script = Files.createTempFile("harness-", isWindows() ? ".cmd" : ".sh");
        // chcp 65001 переключает консоль Windows на UTF-8, иначе русский вывод приедет модели нечитаемым.
        String body = isWindows()
                ? "@echo off\r\nchcp 65001 > nul\r\n" + command + "\r\n"
                : "#!/bin/sh\n" + command + "\n";
        Files.writeString(script, body, StandardCharsets.UTF_8);
        return script;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Запускаем оболочку в корне проекта; stderr идёт в тот же поток, что и stdout.
     */
    private Process startProcess(Path script) throws IOException {
        List<String> shell = isWindows() ? List.of("cmd.exe", "/d", "/c", script.toString()) : List.of("/bin/sh", script.toString());
        ProcessBuilder builder = new ProcessBuilder(shell).directory(workspaceGuard.root().toFile()).redirectErrorStream(true);
        provideJdk(builder.environment());
        Process process = builder.start();
        // Закрываем ввод: команда, которая решит что-то спросить, получит конец файла и завершится.
        process.getOutputStream().close();
        return process;
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


}