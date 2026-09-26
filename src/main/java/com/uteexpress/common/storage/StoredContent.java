package com.uteexpress.common.storage;

public record StoredContent(byte[] bytes, String mediaType) {
    public StoredContent {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
