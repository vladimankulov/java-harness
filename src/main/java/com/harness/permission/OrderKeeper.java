package com.harness.permission;

import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.tooling.guard.WorkspaceGuard;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
//todo: a better name for the class is PermissionManager
public class OrderKeeper {

    private static final Set<PermissionSource> FORBIDDEN_TO_WRITE = Set.of(PermissionSource.APPLICATION, PermissionSource.PROJECT, PermissionSource.USER);

    private final OrderCollector orderCollector;
    private final WorkspaceGuard workspaceGuard;
    private ToolPermissionContext current;


    public OrderKeeper(OrderCollector orderCollector, WorkspaceGuard workspaceGuard) {
        this.orderCollector = orderCollector;
        this.workspaceGuard = workspaceGuard;
        this.current = orderCollector.load();
    }

    public synchronized ToolPermissionContext snapshot() {
        return current;
    }

    public synchronized void apply(PermissionUpdate update) {
        switch (update) {
            case PermissionUpdate.Directory directory -> {
                List<Path> directories = new ArrayList<>();
                directories.add(workspaceGuard.resolve(directory.path()));

                current = new ToolPermissionContext(current.mode(), current.permissionEntries(), directories);
            }
            case PermissionUpdate.Rule rule -> {
                if (FORBIDDEN_TO_WRITE.contains(rule.destination())) {
                    throw new IllegalStateException("Allow to add new rule only in session or local settings");
                }
                PermissionEntry newRule = new PermissionEntry(rule.rule(), rule.behavior(), rule.destination());

                if (newRule.source() == PermissionSource.LOCAL) {
                    orderCollector.saveLocalRule(newRule);
                }

                if (current.permissionEntries().contains(newRule)) {
                    return;
                }

                List<PermissionEntry> rules = new ArrayList<>(current.permissionEntries());
                rules.add(newRule);

                current = new ToolPermissionContext(current.mode(), rules, current.additionalWorkingDirectories());
            }
        }
    }
}
