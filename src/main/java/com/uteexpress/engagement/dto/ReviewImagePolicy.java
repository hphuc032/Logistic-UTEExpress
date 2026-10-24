package com.uteexpress.engagement.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.ImageStoragePolicy;

/** Reuses the existing signature/metadata/dimension-validated image storage. No video in REV-01. */
public final class ReviewImagePolicy {
    public static final int MAX_IMAGES = 3;
    public static final ImageStoragePolicy STORAGE = new ImageStoragePolicy(5L * 1024 * 1024, 4096, 4096);
    private ReviewImagePolicy() { }
    public static void requireCount(int count) {
        if (count < 0 || count > MAX_IMAGES) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }
}
