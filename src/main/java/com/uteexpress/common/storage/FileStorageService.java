package com.uteexpress.common.storage;

public interface FileStorageService {
    StoredFile storeImage(String namespace, UploadContent upload, ImageStoragePolicy policy);

    StoredContent read(String key);

    void delete(String key);
}
