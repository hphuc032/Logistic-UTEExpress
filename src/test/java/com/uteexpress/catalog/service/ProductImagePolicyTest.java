package com.uteexpress.catalog.service;

import com.uteexpress.common.storage.ImageStoragePolicy;
import com.uteexpress.common.storage.LocalFileStorageService;
import com.uteexpress.common.storage.StorageException;
import com.uteexpress.common.storage.StorageProperties;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.support.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImagePolicyTest {
    private static final ImageStoragePolicy POLICY = new ImageStoragePolicy(
            ProductService.IMAGE_MAX_BYTES, ProductService.IMAGE_MAX_DIMENSION,
            ProductService.IMAGE_MAX_DIMENSION);
    @TempDir Path root;

    @Test
    void acceptsDecodedJpegPngWebpAndNeverUsesClientFilenameAsPath() {
        LocalFileStorageService storage = new LocalFileStorageService(new StorageProperties(root));
        var jpeg = storage.storeImage("products", upload(TestImages.jpeg(), "../../evil.jpeg", "image/jpeg"), POLICY);
        var png = storage.storeImage("products", upload(TestImages.png(), "product.png", "image/png"), POLICY);
        var webp = storage.storeImage("products", upload(TestImages.webp(), "product.webp", "image/webp"), POLICY);

        assertThat(jpeg.key()).matches("products/[0-9a-f-]{36}\\.jpg").doesNotContain("evil");
        assertThat(png.key()).endsWith(".png");
        assertThat(webp.key()).endsWith(".webp");
    }

    @Test
    void rejectsOversizedSpoofedTruncatedAndExtremeImages() {
        LocalFileStorageService storage = new LocalFileStorageService(new StorageProperties(root));
        assertReason(() -> storage.storeImage("products", upload(
                new byte[(int) ProductService.IMAGE_MAX_BYTES + 1], "large.png", "image/png"), POLICY),
                StorageException.Reason.TOO_LARGE);
        assertReason(() -> storage.storeImage("products", upload(
                "<html><script>alert(1)</script></html>".getBytes(), "fake.jpg", "image/jpeg"), POLICY),
                StorageException.Reason.UNSUPPORTED_TYPE);
        assertReason(() -> storage.storeImage("products", upload(
                TestImages.png(), "fake.jpg", "image/jpeg"), POLICY),
                StorageException.Reason.UNSUPPORTED_TYPE);
        assertReason(() -> storage.storeImage("products", upload(
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "truncated.jpg", "image/jpeg"), POLICY),
                StorageException.Reason.INVALID_IMAGE);
        assertReason(() -> storage.storeImage("products", upload(
                TestImages.oversizedDimensionPng(), "extreme.png", "image/png"), POLICY),
                StorageException.Reason.INVALID_IMAGE);
        assertReason(() -> storage.storeImage("products", upload(
                "<svg/>".getBytes(), "vector.svg", "image/svg+xml"), POLICY),
                StorageException.Reason.UNSUPPORTED_TYPE);
    }

    private static UploadContent upload(byte[] bytes, String name, String type) {
        return new UploadContent(bytes, name, type);
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            StorageException.Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(StorageException.class,
                error -> assertThat(error.reason()).isEqualTo(reason));
    }
}
