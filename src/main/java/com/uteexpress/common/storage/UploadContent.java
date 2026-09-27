package com.uteexpress.common.storage;

public record UploadContent(byte[] bytes, String originalFilename, String declaredContentType) {
    public UploadContent {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
