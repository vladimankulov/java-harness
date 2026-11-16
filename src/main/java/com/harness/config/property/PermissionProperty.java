package com.harness.config.property;

import com.harness.model.permission.enums.PermissionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("harness.permissions")
public record PermissionProperty(
        @DefaultValue(value = "DEFAULT") PermissionMode mode,
        @DefaultValue(value = "{}") List<String> allow,
        @DefaultValue(value = "{}") List<String> deny,
        @DefaultValue(value = "{}") List<String> ask,
        @DefaultValue(value = "{}") List<String> additionalDirectories) {
}
