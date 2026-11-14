package com.harness.model.tooling;

import com.harness.tooling.guard.FileStateGuard;

public record ToolContext(
    FileStateGuard fileStateGuard
) {

}
