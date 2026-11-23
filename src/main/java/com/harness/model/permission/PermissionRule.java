package com.harness.model.permission;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record PermissionRule(String toolName, @Nullable String argumentPattern) {

    public static PermissionRule parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Rule has null or empty value");
        }
        String text = raw.strip();

        int open = text.indexOf("(");

        if (open < 0) {
            return new PermissionRule(text, null);
        }
        if (!text.endsWith(")")) {
            throw new IllegalArgumentException("Invalid rule: %s, rule must follow the pattern `ToolName(regex)`".formatted(text));
        }
        String toolName = text.substring(0, open).strip();
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("Failed to extract tool name from rule: %s, rule must follow the pattern `ToolName(regex)`".formatted(text));
        }

        String argumentPattern = text.substring(open + 1, text.length() - 1).strip();


        return new PermissionRule(toolName, StringUtils.hasText(argumentPattern) ? argumentPattern : null);
    }

    public boolean appliesTo(String toolName) {
        return this.toolName.equals(toolName);
    }

    @Override
    public String toString() {
        return StringUtils.hasText(argumentPattern) ? "%s(%s)".formatted(this.toolName, this.argumentPattern) : this.toolName;
    }
}
