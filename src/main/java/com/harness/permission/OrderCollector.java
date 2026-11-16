package com.harness.permission;

import com.harness.config.property.PermissionProperty;
import com.harness.model.permission.PermissionEntry;
import com.harness.model.permission.PermissionRule;
import com.harness.model.permission.ToolPermissionContext;
import com.harness.model.permission.enums.PermissionBehavior;
import com.harness.model.permission.enums.PermissionMode;
import com.harness.model.permission.enums.PermissionSource;
import com.harness.tooling.guard.WorkspaceGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Component
public class OrderCollector {

    private final WorkspaceGuard workspaceGuard;
    private final PermissionProperty permissionProperty;
    private final Map<PermissionSource, Path> permissionsFromFiles;

    @Autowired
    public OrderCollector(WorkspaceGuard workspaceGuard, PermissionProperty permissionProperty) {
        this(workspaceGuard, permissionProperty,
                Map.of(
                        PermissionSource.USER, Path.of(System.getProperty("user.home"), ".harness", "permissions.yml"),
                        PermissionSource.PROJECT, workspaceGuard.getRoot().resolve(".harness/permissions.yml"),
                        PermissionSource.LOCAL, workspaceGuard.getRoot().resolve(".harness/permissions.local.yml")));
    }

    public OrderCollector(WorkspaceGuard workspaceGuard, PermissionProperty permissionProperty, Map<PermissionSource, Path> permissionsFromFiles) {
        this.workspaceGuard = workspaceGuard;
        this.permissionProperty = permissionProperty;
        this.permissionsFromFiles = Map.copyOf(permissionsFromFiles);
    }

    public ToolPermissionContext load() {
        List<PermissionEntry> rules = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        addRule(rules, permissionProperty.allow(), PermissionBehavior.ALLOW, PermissionSource.APPLICATION);
        addRule(rules, permissionProperty.deny(), PermissionBehavior.DENY, PermissionSource.APPLICATION);
        addRule(rules, permissionProperty.ask(), PermissionBehavior.ASK, PermissionSource.APPLICATION);

        permissionProperty.additionalDirectories().forEach(dir -> directories.add(workspaceGuard.resolve(dir)));

        PermissionMode mode = permissionProperty.mode();

        for (PermissionSource source : this.permissionsFromFiles.keySet()) {
            Path file = permissionsFromFiles.get(source);

            if (file == null) {
                continue;
            }

            Map<String, Object> yamlSettings = read(file);

            for (PermissionBehavior behavior : PermissionBehavior.values()) {
                addRule(rules, strings(yamlSettings.get(behavior.name().toLowerCase(Locale.ROOT))), behavior, source);
            }

            strings(yamlSettings.get("additional-settings")).forEach(dir -> directories.add(workspaceGuard.resolve(dir)));


            if (yamlSettings.get("mode") != null) {
                mode = PermissionMode.valueOf(yamlSettings.get("mode").toString());
            }
        }


        return new ToolPermissionContext(mode, rules, directories);
    }

    void saveLocalRule(PermissionEntry entry) {
        Path localSettingsFilePath = this.permissionsFromFiles.get(PermissionSource.LOCAL);
        if (localSettingsFilePath == null) {
            throw new IllegalStateException("Local permission source is null");
        }
        Map<String, Object> localSettings = read(localSettingsFilePath);
        String behaviorKey = entry.behavior().name().toLowerCase(Locale.ROOT);

        List<String> ruleSetByBehavior = strings(localSettings.get(behaviorKey));
        String rule = entry.rule().toString();

        if (ruleSetByBehavior.contains(rule)) {
            return;
        }
        ruleSetByBehavior.add(rule);
        try {
            Path directories = Files.createDirectories(localSettingsFilePath.getRoot());

            Path tempLocalSettingsFile = Files.createTempFile(directories, "permission", ".yml");

            try {
                Files.writeString(tempLocalSettingsFile, new Yaml().dump(localSettings));

                try {
                    Files.move(tempLocalSettingsFile, localSettingsFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempLocalSettingsFile, localSettingsFilePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.delete(tempLocalSettingsFile);
            }


        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save local rules in %s file, err: %s".formatted(localSettingsFilePath, e.getMessage()));
        }

    }

    private List<String> strings(Object o) {
        if (o == null) {
            return List.of();
        }

        if (!(o instanceof List<?> list)) {
            throw new IllegalStateException("Failed to get list from object: " + o);
        }

        return list.stream().map(Object::toString).toList();
    }

    private Map<String, Object> read(Path filePath) {
        if (!Files.exists(filePath)) {
            return new LinkedHashMap<>();
        }
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            Object yaml = new Yaml().load(reader);

            if (yaml == null) {
                return new LinkedHashMap<>();
            }

            if (!(yaml instanceof Map<?, ?>)) {
                throw new IllegalStateException("Settings file %s must be valid yaml file".formatted(filePath));
            }
            return new LinkedHashMap<>((Map<String, Object>) yaml);
        } catch (IOException | ClassCastException e) {
            throw new IllegalStateException("Failed to read settings %s, err: %s".formatted(filePath, e.getMessage()));
        }
    }

    private void addRule(List<PermissionEntry> permissionEntries, List<String> allow, PermissionBehavior permissionBehavior, PermissionSource permissionSource) {
        for (String line : allow) {
            permissionEntries.add(new PermissionEntry(PermissionRule.parse(line), permissionBehavior, permissionSource));
        }
    }
}
