package com.uteexpress.common.storage;

import com.uteexpress.support.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {
    private static final ImageStoragePolicy POLICY = new ImageStoragePolicy(2 * 1024 * 1024, 4096, 4096);
    @TempDir Path root;

    @Test
    void storesDecodesAndReadsJpegPngAndWebpWithServerGeneratedKeys() {
        LocalFileStorageService storage = storage();

        StoredFile jpeg = storage.storeImage("avatars",
                upload(TestImages.jpeg(), "client-name.jpeg", "image/jpeg"), POLICY);
        StoredFile png = storage.storeImage("avatars",
                upload(TestImages.png(), "client-name.png", "image/png"), POLICY);
        StoredFile webp = storage.storeImage("avatars",
                upload(TestImages.webp(), "client-name.webp", "image/webp"), POLICY);

        assertThat(jpeg.key()).matches("avatars/[0-9a-f-]{36}\\.jpg");
        assertThat(png.key()).matches("avatars/[0-9a-f-]{36}\\.png");
        assertThat(webp.key()).matches("avatars/[0-9a-f-]{36}\\.webp");
        assertThat(jpeg.key()).doesNotContain("client-name");
        assertThat(storage.read(jpeg.key()).mediaType()).isEqualTo("image/jpeg");
        assertThat(storage.read(png.key()).bytes()).isEqualTo(TestImages.png());
        assertThat(storage.read(webp.key()).mediaType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsSpoofedTruncatedOversizedAndExtremeImages() {
        LocalFileStorageService storage = storage();

        assertReason(() -> storage.storeImage("avatars",
                upload(TestImages.png(), "avatar.jpg", "image/jpeg"), POLICY),
                StorageException.Reason.UNSUPPORTED_TYPE);
        assertReason(() -> storage.storeImage("avatars",
                upload(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "a.jpg", "image/jpeg"), POLICY),
                StorageException.Reason.INVALID_IMAGE);
        assertReason(() -> storage.storeImage("avatars",
                upload(new byte[(2 * 1024 * 1024) + 1], "a.png", "image/png"), POLICY),
                StorageException.Reason.TOO_LARGE);
        assertReason(() -> storage.storeImage("avatars",
                upload(TestImages.oversizedDimensionPng(), "a.png", "image/png"), POLICY),
                StorageException.Reason.INVALID_IMAGE);
        assertReason(() -> storage.storeImage("avatars",
                upload("<svg><script>alert(1)</script></svg>".getBytes(), "a.svg", "image/svg+xml"), POLICY),
                StorageException.Reason.UNSUPPORTED_TYPE);
    }

    @Test
    void confinesAllKeysToConfiguredRootAndDeletesOnlyTrustedKeys() throws Exception {
        LocalFileStorageService storage = storage();
        StoredFile stored = storage.storeImage("avatars",
                upload(TestImages.png(), "avatar.png", "image/png"), POLICY);
        Path storedPath = root.resolve(stored.key().replace('/', java.io.File.separatorChar));
        assertThat(Files.exists(storedPath)).isTrue();

        assertThatThrownBy(() -> storage.resolveKey("avatars/../../outside.png"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.resolveKey("avatars/.."))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.resolveKey("avatars/not-a-server-uuid.png"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.resolveKey("C:/outside.png"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.resolveKey("avatars\\outside.png"))
                .isInstanceOf(StorageException.class);

        storage.delete(stored.key());
        assertThat(Files.exists(storedPath)).isFalse();
    }

    private LocalFileStorageService storage() {
        return new LocalFileStorageService(new StorageProperties(root));
    }

    private static UploadContent upload(byte[] bytes, String name, String type) {
        return new UploadContent(bytes, name, type);
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            StorageException.Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(StorageException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }
}
