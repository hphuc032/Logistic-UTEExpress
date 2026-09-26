package com.uteexpress.account.dto;

public record AvatarUpload(byte[] bytes, String originalFilename, String contentType) {
    public AvatarUpload {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() { return bytes.clone(); }
}
