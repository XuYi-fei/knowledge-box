package com.knowledgebox.service.integration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.knowledgebox.config.KnowledgeBoxProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkillPackageStorageService {

    private final KnowledgeBoxProperties properties;

    public SkillPackageStorageService(KnowledgeBoxProperties properties) {
        this.properties = properties;
    }

    public StoredSkillPackage store(String code, byte[] zipBytes) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Skill code is required");
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("Skill package bytes are empty");
        }

        KnowledgeBoxProperties.Oss oss = properties.getStorage().getOss();
        String endpoint = require(oss.getEndpoint(), "knowledge-box.storage.oss.endpoint");
        String bucket = require(oss.getBucket(), "knowledge-box.storage.oss.bucket");
        String accessKeyId = require(oss.getAccessKeyId(), "knowledge-box.storage.oss.access-key-id");
        String accessKeySecret = require(oss.getAccessKeySecret(), "knowledge-box.storage.oss.access-key-secret");
        String md5 = hexMd5(zipBytes);
        String safeCode = sanitizeCode(code);
        String objectKey = buildObjectKey(oss.getPathPrefix(), properties.getIntegration().getSkillPackageCategory(), safeCode, md5);

        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            if (!client.doesObjectExist(bucket, objectKey)) {
                client.putObject(bucket, objectKey, new ByteArrayInputStream(zipBytes));
            }
            return new StoredSkillPackage(objectKey, md5, zipBytes.length);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store skill package to OSS", exception);
        } finally {
            client.shutdown();
        }
    }

    public byte[] load(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("Skill objectKey is required");
        }
        KnowledgeBoxProperties.Oss oss = properties.getStorage().getOss();
        String endpoint = require(oss.getEndpoint(), "knowledge-box.storage.oss.endpoint");
        String bucket = require(oss.getBucket(), "knowledge-box.storage.oss.bucket");
        String accessKeyId = require(oss.getAccessKeyId(), "knowledge-box.storage.oss.access-key-id");
        String accessKeySecret = require(oss.getAccessKeySecret(), "knowledge-box.storage.oss.access-key-secret");

        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (OSSObject object = client.getObject(new GetObjectRequest(bucket, objectKey));
             var inputStream = object.getObjectContent();
             var outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load skill package from OSS: " + objectKey, exception);
        } finally {
            client.shutdown();
        }
    }

    private String require(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(key + " must be configured when storing skill packages");
        }
        return value.trim();
    }

    private String sanitizeCode(String code) {
        String normalized = code.strip().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Skill code is empty");
        }
        if (!normalized.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Skill code must match [a-z0-9._-]+");
        }
        return normalized;
    }

    private String buildObjectKey(String pathPrefix, String category, String skillCode, String md5) {
        String normalizedPrefix = pathPrefix == null ? "" : pathPrefix.strip();
        normalizedPrefix = normalizedPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
        String normalizedCategory = category == null ? "agent-skills" : category.strip();
        normalizedCategory = normalizedCategory.replaceAll("^/+", "").replaceAll("/+$", "");
        String body = normalizedCategory + "/" + skillCode + "/" + md5 + ".skill.zip";
        if (!StringUtils.hasText(normalizedPrefix)) {
            return body;
        }
        return normalizedPrefix + "/" + body;
    }

    private String hexMd5(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute skill package md5", exception);
        }
    }

    public record StoredSkillPackage(String objectKey, String checksumMd5, long contentLength) {
    }
}
