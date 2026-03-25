package com.knowledgebox.service.document;

import com.knowledgebox.config.KnowledgeBoxProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "knowledge-box.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final KnowledgeBoxProperties properties;

    public LocalStorageService(KnowledgeBoxProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject store(String category, MultipartFile file) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file.bin" : file.getOriginalFilename());
        String safeName = UUID.randomUUID() + "-" + originalName;
        return storeLocal(category, safeName, file, false);
    }

    @Override
    public StoredObject storeDeterministic(String category, String objectName, MultipartFile file) {
        String safeName = sanitizeObjectName(objectName);
        return storeLocal(category, safeName, file, true);
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolveObjectPath(objectKey));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read file from local storage: " + objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveObjectPath(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete file from local storage: " + objectKey, exception);
        }
    }

    private StoredObject storeLocal(String category, String safeName, MultipartFile file, boolean skipIfExists) {
        Path baseDir = Path.of(properties.getStorage().getLocalBasePath()).resolve(category);
        String objectKey = category + "/" + safeName;
        try {
            Files.createDirectories(baseDir);
            Path targetFile = baseDir.resolve(safeName);
            if (!skipIfExists || Files.notExists(targetFile)) {
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return new StoredObject(
                    "local",
                    objectKey,
                    "/uploads/" + objectKey,
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store file: " + safeName, exception);
        }
    }

    private String sanitizeObjectName(String objectName) {
        String safeName = StringUtils.cleanPath(objectName == null ? "" : objectName);
        if (!StringUtils.hasText(safeName) || safeName.contains("..") || safeName.contains("/") || safeName.contains("\\")) {
            throw new IllegalArgumentException("Invalid object name for deterministic storage");
        }
        return safeName;
    }

    private Path resolveObjectPath(String objectKey) {
        String safeKey = StringUtils.cleanPath(objectKey == null ? "" : objectKey);
        if (!StringUtils.hasText(safeKey) || safeKey.contains("..")) {
            throw new IllegalArgumentException("Invalid object key for local storage read");
        }
        return Path.of(properties.getStorage().getLocalBasePath()).resolve(safeKey);
    }
}
