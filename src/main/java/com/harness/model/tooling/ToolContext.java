package com.harness.model.tooling;

import com.harness.model.permission.ToolPermissionContext;
import com.harness.permission.OrderKeeper;
import com.harness.tooling.guard.FileStateGuard;
import org.springframework.stereotype.Component;

@Component
public record ToolContext(
        FileStateGuard fileStateGuard,
        OrderKeeper orderKeeper
) {

    public ToolPermissionContext permissions() {
        return orderKeeper.snapshot();
    }
}
