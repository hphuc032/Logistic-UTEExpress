package com.uteexpress.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Set<String> SAFE_NAMESPACES = Set.of("avatars", "products", "reviews");
    private static final Pattern SERVER_KEY = Pattern.compile(
            "^(avatars|products|reviews)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");
    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        root = properties.root().toAbsolutePath().normalize();
    }

    @Override
    public StoredFile storeImage(String namespace, UploadContent upload, ImageStoragePolicy policy) {
        if (!SAFE_NAMESPACES.contains(namespace)) {
            throw new StorageException(StorageException.Reason.INVALID_PATH);
        }
        byte[] bytes = upload.bytes();
        if (bytes.length == 0) {
            throw new StorageException(StorageException.Reason.EMPTY);
        }
        if (bytes.length > policy.maxBytes()) {
            throw new StorageException(StorageException.Reason.TOO_LARGE);
        }

        ImageType type = ImageType.detect(bytes);
        type.requireMatchingMetadata(upload.originalFilename(), upload.declaredContentType());
        Dimensions dimensions = decodeDimensions(bytes, type, policy);
        String key = namespace + "/" + UUID.randomUUID() + "." + type.extension;
        Path target = resolveKey(key);
        Path temporary = null;
        try {
            target = safeWritableTarget(target);
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temporary, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
            return new StoredFile(key, type.mediaType, dimensions.width, dimensions.height);
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new StorageException(StorageException.Reason.IO_FAILURE, exception);
        }
    }

    @Override
    public StoredContent read(String key) {
        Path path = resolveKey(key);
        ImageType type = ImageType.fromServerKey(key);
        try {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                    || !path.toRealPath().startsWith(realRoot())) {
                throw new StorageException(StorageException.Reason.INVALID_PATH);
            }
            return new StoredContent(Files.readAllBytes(path), type.mediaType);
        } catch (IOException exception) {
            throw new StorageException(StorageException.Reason.IO_FAILURE, exception);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            Path path = resolveKey(key);
            if (Files.exists(path) && !path.toRealPath().startsWith(realRoot())) {
                throw new StorageException(StorageException.Reason.INVALID_PATH);
            }
            Files.deleteIfExists(path);
        } catch (IOException | StorageException exception) {
            log.warn("Could not delete stored file with a server-owned key");
        }
    }

    Path resolveKey(String key) {
        try {
            if (key == null || key.isBlank() || key.indexOf('\\') >= 0
                    || !SERVER_KEY.matcher(key).matches()) {
                throw new StorageException(StorageException.Reason.INVALID_PATH);
            }
            Path relative = Path.of(key);
            if (relative.isAbsolute() || relative.getNameCount() != 2
                    || !SAFE_NAMESPACES.contains(relative.getName(0).toString())) {
                throw new StorageException(StorageException.Reason.INVALID_PATH);
            }
            Path resolved = root.resolve(relative).normalize();
            if (!resolved.startsWith(root)) {
                throw new StorageException(StorageException.Reason.INVALID_PATH);
            }
            return resolved;
        } catch (InvalidPathException exception) {
            throw new StorageException(StorageException.Reason.INVALID_PATH, exception);
        }
    }

    private Path safeWritableTarget(Path target) throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(target.getParent());
        Path realParent = target.getParent().toRealPath();
        if (!realParent.startsWith(realRoot())) {
            throw new StorageException(StorageException.Reason.INVALID_PATH);
        }
        return realParent.resolve(target.getFileName());
    }

    private Path realRoot() throws IOException {
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private static Dimensions decodeDimensions(byte[] bytes, ImageType expected, ImageStoragePolicy policy) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new StorageException(StorageException.Reason.INVALID_IMAGE);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new StorageException(StorageException.Reason.INVALID_IMAGE);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!expected.matchesFormat(format)) {
                    throw new StorageException(StorageException.Reason.INVALID_IMAGE);
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > policy.maxWidth() || height > policy.maxHeight()) {
                    throw new StorageException(StorageException.Reason.INVALID_IMAGE);
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw new StorageException(StorageException.Reason.INVALID_IMAGE);
                }
                return new Dimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof StorageException storage) throw storage;
            throw new StorageException(StorageException.Reason.INVALID_IMAGE, exception);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original storage error remains authoritative.
        }
    }

    private record Dimensions(int width, int height) { }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp");

        private final String extension;
        private final String mediaType;

        ImageType(String extension, String mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        static ImageType detect(byte[] bytes) {
            if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                    && unsigned(bytes[2]) == 0xff) return JPEG;
            if (bytes.length >= 8 && unsigned(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                    && bytes[3] == 'G' && unsigned(bytes[4]) == 0x0d && unsigned(bytes[5]) == 0x0a
                    && unsigned(bytes[6]) == 0x1a && unsigned(bytes[7]) == 0x0a) return PNG;
            if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return WEBP;
            throw new StorageException(StorageException.Reason.UNSUPPORTED_TYPE);
        }

        static ImageType fromServerKey(String key) {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".jpg")) return JPEG;
            if (normalized.endsWith(".png")) return PNG;
            if (normalized.endsWith(".webp")) return WEBP;
            throw new StorageException(StorageException.Reason.INVALID_PATH);
        }

        void requireMatchingMetadata(String filename, String contentType) {
            String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            boolean extensionMatches = switch (this) {
                case JPEG -> lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                case PNG -> lower.endsWith(".png");
                case WEBP -> lower.endsWith(".webp");
            };
            if (!extensionMatches || contentType == null || !mediaType.equalsIgnoreCase(contentType.trim())) {
                throw new StorageException(StorageException.Reason.UNSUPPORTED_TYPE);
            }
        }

        boolean matchesFormat(String format) {
            return switch (this) {
                case JPEG -> format.equals("jpeg") || format.equals("jpg");
                case PNG -> format.equals("png");
                case WEBP -> format.equals("webp");
            };
        }

        private static int unsigned(byte value) { return value & 0xff; }

        private static boolean ascii(byte[] bytes, int offset, String text) {
            for (int index = 0; index < text.length(); index++) {
                if (bytes[offset + index] != (byte) text.charAt(index)) return false;
            }
            return true;
        }
    }
}
