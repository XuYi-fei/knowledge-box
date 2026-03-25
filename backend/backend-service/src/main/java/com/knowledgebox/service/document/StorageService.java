package com.knowledgebox.service.document;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    StoredObject store(String category, MultipartFile file);

    default StoredObject storeDeterministic(String category, String objectName, MultipartFile file) {
        return store(category, file);
    }

    byte[] read(String objectKey);

    void delete(String objectKey);

    record StoredObject(
            String provider,
            String objectKey,
            String url,
            String contentType,
            Long contentLength
    ) {
    }
}
