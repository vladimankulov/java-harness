package fixtures;

import com.harness.agent.property.AgentProperty;
import com.harness.config.property.PermissionProperty;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.model.tooling.ToolContext;
import com.harness.permission.*;
import com.harness.tooling.Tool;
import com.harness.tooling.ToolRegistry;
import com.harness.tooling.guard.FileStateGuard;
import com.harness.tooling.guard.WorkspaceGuard;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * Ручная сборка той же цепочки для тестов, без чтения личных настроек преподавателя.
 */
@TestConfiguration
public class TestPermissionConfig {
    @Bean
    PermissionPrompt permissionPrompt() {
        return allowOnce();
    }

    public static PermissionPrompt allowOnce() {
        return (tool, target, reason) -> PermissionPrompt.Answer.ALLOW_ONCE;
    }

    public static PermissionProperty permissive() {
        return properties(PermissionMode.BYPASS_PERMISSIONS, List.of(), List.of(), List.of());
    }

    public static PermissionProperty properties(
            PermissionMode mode, List<String> allow, List<String> deny, List<String> ask) {
        return new PermissionProperty(mode, allow, deny, ask, List.of());
    }

    public static ToolContext context(FileStateGuard cache) {
        var guard = new WorkspaceGuard(new AgentProperty(20, ".", true));
        var collector = new OrderCollector(guard, properties(PermissionMode.DEFAULT, List.of(), List.of(), List.of()), Map.of());
        return new ToolContext(cache, new OrderKeeper(collector, guard));
    }

    public record Setup(
            ToolContext context, PermissionEvaluator evaluator, OrderKeeper orders) {
    }

    public static Setup setup(PermissionProperty p, WorkspaceGuard guard, FileStateGuard cache) {
        // Свои временные файлы user/project/local: «запомнить» пишет в local, личные настройки не читаем и не трогаем.
        var collector = new OrderCollector(guard, p, temporaryOrdersFiles());
        var orders = new OrderKeeper(collector, guard);
        return new Setup(
                new ToolContext(cache, orders),
                new PermissionEvaluator(orders),
                orders);
    }

    private static Map<PermissionSource, java.nio.file.Path> temporaryOrdersFiles() {
        try {
            var directory = java.nio.file.Files.createTempDirectory("harness-permissions-");
            directory.toFile().deleteOnExit();
            Map<PermissionSource, java.nio.file.Path> files = new java.util.HashMap<>();
            for (PermissionSource source : List.of(PermissionSource.USER, PermissionSource.PROJECT, PermissionSource.LOCAL)) {
                var file = directory.resolve(source + ".yaml");
                file.toFile().deleteOnExit();
                files.put(source, file);
            }
            return files;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static PermissionEvaluator evaluator(PermissionProperty p) {
        return setup(
                p,
                new WorkspaceGuard(new AgentProperty(20, ".", true)),
                new FileStateGuard())
                .evaluator();
    }

    public static ToolRegistry registry(
            List<Tool<?>> tools,
            ApplicationEventPublisher events,
            FileStateGuard cache,
            PermissionProperty p,
            PermissionPrompt prompt) {
        var setup = setup(p, new WorkspaceGuard(new AgentProperty(20, ".", true)), cache);
        return new ToolRegistry(
                tools,
                events,
                setup.context(),
                new PermissionGate(setup.evaluator(), prompt, events));
    }
}
