package com.uteexpress.common.storage;

public record ImageStoragePolicy(long maxBytes, int maxWidth, int maxHeight) {
    public ImageStoragePolicy {
        if (maxBytes < 1 || maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Image storage limits must be positive");
        }
    }
}
