package com.harness.model.permission.enums;

public enum PermissionSource {
    //application.yml - default
    APPLICATION,
    // ~/.harness/permissions.yml
    USER,
    // .harness/permissions.yml - settings for the current project
    PROJECT,
    // .harness/permissions.local.yml - settings accepted during the ongoing conversation for the current project
    LOCAL,
    //during the ongoing conversation
    SESSION
}
