package com.uteexpress.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("uteexpress.storage")
public record StorageProperties(Path root) {
    public StorageProperties {
        if (root == null) {
            throw new IllegalStateException("Storage root must be configured");
        }
    }
}
