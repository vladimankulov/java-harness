package com.harness.permission;

import com.harness.agent.property.AgentProperty;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.tooling.guard.WorkspaceGuard;
import fixtures.TestPermissionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class OrdersKeeperTest {
    @TempDir
    Path root;

    private WorkspaceGuard guard() {
        return new WorkspaceGuard(new AgentProperty(20, root.toString(), true));
    }

    private OrderCollector collector() {
        return new OrderCollector(
                guard(),
                TestPermissionConfig.properties(
                        PermissionMode.DEFAULT, List.of(), List.of(), List.of()),
                Map.of(
                        PermissionSource.USER,
                        root.resolve("user.yaml"),
                        PermissionSource.PROJECT,
                        root.resolve("project.yaml"),
                        PermissionSource.LOCAL,
                        root.resolve("local.yaml")));
    }

    private OrderKeeper store() {
        return new OrderKeeper(collector(), guard());
    }

    @Test
    void правилаВсехИсточниковСохраняютсяАРежимБерётсяИзПоследнего() throws Exception {
        Files.writeString(root.resolve("user.yaml"), "mode: DEFAULT\ndeny: ['Read(secret/**)']\n");
        Files.writeString(
                root.resolve("project.yaml"), "mode: ACCEPT_EDITS\nallow: ['Read(secret/**)']\n");
        var loaded = store().snapshot();
        assertThat(loaded.mode()).isEqualTo(PermissionMode.ACCEPT_EDITS);
        assertThat(loaded.permissionEntries())
                .extracting(PermissionEntry::behavior)
                .containsExactly(PermissionBehavior.DENY, PermissionBehavior.ALLOW);
    }

    @Test
    void сохранённоеПравилоЧитаетсяПослеНовогоЗапускаАСессионноеНет() {
        var store = store();
        store.apply(
                PermissionUpdate.rule(
                        PermissionRule.parse("Bash(./mvnw test*)"),
                        PermissionBehavior.ALLOW,
                        PermissionSource.LOCAL));
        store.apply(PermissionUpdate.rule(PermissionRule.parse("Bash(echo once)"), PermissionBehavior.ALLOW, PermissionSource.SESSION));
        assertThat(store.snapshot().permissionEntries()).hasSize(2);
        assertThat(store().snapshot().permissionEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.rule().argumentPattern()).isEqualTo("./mvnw test*"));
    }

    @Test
    void правкаФайлаВступаетВСилуПриЗапускеАСогласиеСразу() throws Exception {
        var store = store();
        Files.writeString(root.resolve("local.yaml"), "mode: BYPASS_PERMISSIONS\nask: ['Edit(src/**)']\n");
        store.apply(PermissionUpdate.rule(PermissionRule.parse("Bash(echo ok)"), PermissionBehavior.ALLOW, PermissionSource.LOCAL));
        // Режим прочитан при старте и в работе не меняется: правка файла его не подменяет.
        assertThat(store.snapshot().mode()).isEqualTo(PermissionMode.DEFAULT);
        assertThat(store.snapshot().permissionEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.behavior()).isEqualTo(PermissionBehavior.ALLOW));
        var afterRestart = store().snapshot();
        assertThat(afterRestart.mode()).isEqualTo(PermissionMode.BYPASS_PERMISSIONS);
        assertThat(afterRestart.permissionEntries()).extracting(PermissionEntry::behavior)
                .containsExactlyInAnyOrder(PermissionBehavior.ASK, PermissionBehavior.ALLOW);
    }

    @Test
    void сбойЗаписиНеПубликуетПостоянноеСогласие() throws Exception {
        var store = store();
        store.apply(PermissionUpdate.rule(PermissionRule.parse("Bash(echo ok)"), PermissionBehavior.ALLOW, PermissionSource.SESSION));
        Files.writeString(root.resolve("local.yaml"), "deny: [broken\n");
        assertThatThrownBy(() -> store.apply(PermissionUpdate.rule(
                PermissionRule.parse("Bash(echo local)"), PermissionBehavior.ALLOW, PermissionSource.LOCAL)))
                .isInstanceOf(org.yaml.snakeyaml.error.YAMLException.class);
        assertThat(store.snapshot().permissionEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.source()).isEqualTo(PermissionSource.SESSION));
    }
}
