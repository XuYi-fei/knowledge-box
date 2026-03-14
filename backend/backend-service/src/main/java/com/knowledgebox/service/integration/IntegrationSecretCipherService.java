package com.knowledgebox.service.integration;

import com.knowledgebox.config.KnowledgeBoxProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationSecretCipherService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final KnowledgeBoxProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public IntegrationSecretCipherService(KnowledgeBoxProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt integration secret", exception);
        }
    }

    public String decrypt(String encryptedText) {
        if (!StringUtils.hasText(encryptedText)) {
            return "";
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= GCM_IV_LENGTH) {
                throw new IllegalStateException("Encrypted payload is invalid");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherBytes = new byte[payload.length - GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(payload, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt integration secret", exception);
        }
    }

    private SecretKeySpec resolveKey() {
        String configured = properties.getIntegration().getCrypto().getMasterKey();
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException(
                    "knowledge-box.integration.crypto.master-key must be configured before using MCP secret storage"
            );
        }
        try {
            byte[] input;
            if (configured.startsWith("base64:")) {
                input = Base64.getDecoder().decode(configured.substring("base64:".length()));
            } else {
                input = configured.getBytes(StandardCharsets.UTF_8);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return new SecretKeySpec(digest, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build encryption key from integration master-key", exception);
        }
    }
}
