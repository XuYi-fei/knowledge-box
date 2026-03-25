package com.knowledgebox.service.document;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.knowledgebox.config.KnowledgeBoxProperties;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "knowledge-box.storage", name = "provider", havingValue = "oss")
public class OssStorageService implements StorageService {

    private final KnowledgeBoxProperties properties;

    public OssStorageService(KnowledgeBoxProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject store(String category, MultipartFile file) {
        KnowledgeBoxProperties.Oss oss = properties.getStorage().getOss();
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file.bin" : file.getOriginalFilename());
        String safeName = UUID.randomUUID() + "-" + originalName;
        return storeToOss(oss, category, safeName, file, false);
    }

    @Override
    public StoredObject storeDeterministic(String category, String objectName, MultipartFile file) {
        KnowledgeBoxProperties.Oss oss = properties.getStorage().getOss();
        String safeName = sanitizeObjectName(objectName);
        return storeToOss(oss, category, safeName, file, true);
    }

    @Override
    public byte[] read(String objectKey) {
        KnowledgeBoxProperties.Oss oss = properties.getStorage().getOss();
        String endpoint = require(oss.getEndpoint(), "knowledge-box.storage.oss.endpoint");
        String bucket = require(oss.getBucket(), "knowledge-box.storage.oss.bucket");
        String accessKeyId = require(oss.getAccessKeyId(), "knowledge-box.storage.oss.access-key-id");
        String accessKeySecret = require(oss.getAccessKeySecret(), "knowledge-box.storage.oss.access-key-secret");
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream inputStream = client.getObject(bucket, objectKey).getObjectContent()) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read file from OSS: " + objectKey, exception);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void delete(String objectKey) {
        KnowledgeBoxProperties.Oss oss = properties.getStorage().getOss();
        String endpoint = require(oss.getEndpoint(), "knowledge-box.storage.oss.endpoint");
        String bucket = require(oss.getBucket(), "knowledge-box.storage.oss.bucket");
        String accessKeyId = require(oss.getAccessKeyId(), "knowledge-box.storage.oss.access-key-id");
        String accessKeySecret = require(oss.getAccessKeySecret(), "knowledge-box.storage.oss.access-key-secret");
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            if (client.doesObjectExist(bucket, objectKey)) {
                client.deleteObject(bucket, objectKey);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete file from OSS: " + objectKey, exception);
        } finally {
            client.shutdown();
        }
    }

    private StoredObject storeToOss(
            KnowledgeBoxProperties.Oss oss,
            String category,
            String safeName,
            MultipartFile file,
            boolean skipIfExists
    ) {
        String endpoint = require(oss.getEndpoint(), "knowledge-box.storage.oss.endpoint");
        String bucket = require(oss.getBucket(), "knowledge-box.storage.oss.bucket");
        String accessKeyId = require(oss.getAccessKeyId(), "knowledge-box.storage.oss.access-key-id");
        String accessKeySecret = require(oss.getAccessKeySecret(), "knowledge-box.storage.oss.access-key-secret");
        String objectKey = buildObjectKey(oss.getPathPrefix(), category, safeName);

        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            if (!skipIfExists || !client.doesObjectExist(bucket, objectKey)) {
                try (InputStream inputStream = file.getInputStream()) {
                    client.putObject(bucket, objectKey, inputStream);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store file to OSS: " + safeName, exception);
        } finally {
            client.shutdown();
        }

        return new StoredObject(
                "oss",
                objectKey,
                resolvePublicUrl(oss, bucket, endpoint, objectKey),
                file.getContentType(),
                file.getSize()
        );
    }

    private String sanitizeObjectName(String objectName) {
        String safeName = StringUtils.cleanPath(objectName == null ? "" : objectName);
        if (!StringUtils.hasText(safeName) || safeName.contains("..") || safeName.contains("/") || safeName.contains("\\")) {
            throw new IllegalArgumentException("Invalid object name for deterministic storage");
        }
        return safeName;
    }

    private String buildObjectKey(String prefix, String category, String safeName) {
        String normalizedPrefix = prefix == null ? "" : prefix.strip();
        if (!normalizedPrefix.isBlank()) {
            normalizedPrefix = normalizedPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
            return normalizedPrefix + "/" + category + "/" + safeName;
        }
        return category + "/" + safeName;
    }

    private String resolvePublicUrl(KnowledgeBoxProperties.Oss oss, String bucket, String endpoint, String objectKey) {
        String encodedObjectKey = URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
        if (StringUtils.hasText(oss.getPublicBaseUrl())) {
            String base = oss.getPublicBaseUrl().replaceAll("/+$", "");
            return base + "/" + encodedObjectKey;
        }
        String host = endpoint.replaceFirst("^https?://", "");
        return "https://" + bucket + "." + host + "/" + encodedObjectKey;
    }

    private String require(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(key + " must be configured when using OSS storage provider");
        }
        return value.trim();
    }
}
