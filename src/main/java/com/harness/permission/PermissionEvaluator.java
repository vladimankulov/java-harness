package com.harness.permission;

import com.harness.model.permission.PermissionDecision;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.tooling.ToolContext;
import com.harness.tooling.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionEvaluator {
    private final OrdersKeeper ordersKeeper;

    public PermissionEvaluator(OrdersKeeper ordersKeeper) {
        this.ordersKeeper = ordersKeeper;
    }

    /** Правило на весь инструмент скрывает его из списка модели; запрет отдельного пути — нет. */
    public boolean isAvailable(Tool<?> tool, ToolContext context) {
        return context.permissions()
                .wholeToolRule(tool.name(), PermissionBehavior.DENY) == null;
    }

    public void remember(List<PermissionUpdate> suggestions) {
        suggestions.forEach(ordersKeeper::apply);
    }

    public PermissionMode mode() {
        // HUD получает текущий режим без запуска проверки конкретного вызова.
        return ordersKeeper.snapshot().mode();
    }

    /** Только подсказка для стартового экрана. Конкретный вызов проверит evaluate. */
    public boolean isLocked(Tool<?> tool) {
        // Только значок защиты для HUD: это приблизительная подсказка, а не разрешение на выполнение.
        PermissionMode mode = mode();
        return !tool.isReadOnly() && mode != PermissionMode.BYPASS_PERMISSIONS && !(mode == PermissionMode.ACCEPT_EDITS && (tool.name().equals("Edit") || tool.name().equals("Write")));
    }

    public <I> PermissionDecision evaluate(Tool<I> tool, I input, ToolContext context) {
        ToolPermissionContext settings = context.permissions();

        PermissionEntry denyFromSettings = settings.wholeToolRule(tool.name(), PermissionBehavior.DENY);
        if (denyFromSettings != null) {
            return PermissionDecision.deny("Denied by rule: %s".formatted(denyFromSettings.rule().toString()));
        }
        PermissionDecision fromTool = tool.checkPermission(input, context);
        if (fromTool.behavior() == PermissionBehavior.DENY) {
            return fromTool;
        }

        if (fromTool.askAlways()) {
            return fromTool;
        }

        PermissionEntry askFromSettings = settings.wholeToolRule(tool.name(), PermissionBehavior.ASK);
        if (askFromSettings != null) {
            return PermissionDecision.ask("Ask user according the rule: %s".formatted(askFromSettings.rule().toString()));
        }



        if (settings.mode() == PermissionMode.BYPASS_PERMISSIONS) {
            return PermissionDecision.allow("Allowed by bypass permissions");
        }
        PermissionEntry allowFromSettings = settings.wholeToolRule(tool.name(), PermissionBehavior.ALLOW);
        if (allowFromSettings != null) {
            return PermissionDecision.ask("Allowed by the rule: %s".formatted(allowFromSettings.rule().toString()));
        }

        return fromTool;
    }
}
