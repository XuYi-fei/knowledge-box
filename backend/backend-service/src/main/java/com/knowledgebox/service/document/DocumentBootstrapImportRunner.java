package com.knowledgebox.service.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.CreateDocumentReviewRequest;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.service.admin.AdminOperatorService;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentBootstrapImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentBootstrapImportRunner.class);
    private static final TypeReference<List<BootstrapSeedItem>> SEED_ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private static final String LOG_PREFIX = "[DOCUMENT-BOOTSTRAP]";

    private final KnowledgeBoxProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final AdminOperatorService adminOperatorService;
    private final DocumentGovernanceService documentGovernanceService;
    private final DocumentReviewRequestRepository documentReviewRequestRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public DocumentBootstrapImportRunner(
            KnowledgeBoxProperties properties,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            AdminOperatorService adminOperatorService,
            DocumentGovernanceService documentGovernanceService,
            DocumentReviewRequestRepository documentReviewRequestRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.adminOperatorService = adminOperatorService;
        this.documentGovernanceService = documentGovernanceService;
        this.documentReviewRequestRepository = documentReviewRequestRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeBoxProperties.Bootstrap bootstrap = properties.getDocument().getBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path resolvedSeedFile = StringUtils.hasText(bootstrap.getSeedFile()) ? resolveSeedFilePath(bootstrap.getSeedFile()) : null;
        Path resolvedSeedDirectory = StringUtils.hasText(bootstrap.getSeedDirectory()) ? resolveSeedDirectoryPath(bootstrap.getSeedDirectory()) : null;
        log.info(
                "{} ===== import start ===== cwd={}, seedFile={}, resolvedSeedFile={}, seedDirectory={}, resolvedSeedDirectory={}, pattern={}, recursive={}, failFast={}",
                LOG_PREFIX,
                workingDirectory,
                bootstrap.getSeedFile(),
                resolvedSeedFile,
                bootstrap.getSeedDirectory(),
                resolvedSeedDirectory,
                bootstrap.getSeedDirectoryPattern(),
                bootstrap.isSeedDirectoryRecursive(),
                bootstrap.isFailFast()
        );
        if (!StringUtils.hasText(bootstrap.getSeedFile()) && !StringUtils.hasText(bootstrap.getSeedDirectory())) {
            log.warn("Document bootstrap is enabled but both seed-file and seed-directory are blank, skip import.");
            return;
        }
        BootstrapRunSummary summary = importConfiguredSeeds(bootstrap);
        log.info(
                "{} ===== import completed ===== created={}, skipped={}, failed={}, seedFile={}, seedDirectory={}",
                LOG_PREFIX,
                summary.createdCount(),
                summary.skippedCount(),
                summary.failedCount(),
                bootstrap.getSeedFile(),
                bootstrap.getSeedDirectory()
        );
    }

    BootstrapRunSummary importConfiguredSeeds(KnowledgeBoxProperties.Bootstrap bootstrap) {
        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        Set<Path> importedSeedPaths = new LinkedHashSet<>();

        if (StringUtils.hasText(bootstrap.getSeedFile())) {
            BootstrapRunSummary summary = importFromSeedFile(bootstrap.getSeedFile(), bootstrap.isFailFast());
            created += summary.createdCount();
            skipped += summary.skippedCount();
            failed += summary.failedCount();
            errors.addAll(summary.errors());
            Path seedFilePath = resolveSeedFilePath(bootstrap.getSeedFile());
            if (seedFilePath != null) {
                importedSeedPaths.add(seedFilePath);
            }
        }

        if (StringUtils.hasText(bootstrap.getSeedDirectory())) {
            BootstrapRunSummary summary = importFromSeedDirectory(
                    bootstrap.getSeedDirectory(),
                    bootstrap.getSeedDirectoryPattern(),
                    bootstrap.isSeedDirectoryRecursive(),
                    bootstrap.isFailFast(),
                    importedSeedPaths
            );
            created += summary.createdCount();
            skipped += summary.skippedCount();
            failed += summary.failedCount();
            errors.addAll(summary.errors());
        }

        return new BootstrapRunSummary(created, skipped, failed, errors);
    }

    BootstrapRunSummary importFromSeedFile(String seedFile, boolean failFast) {
        Resource seedResource = resolveSeedResource(seedFile);
        Path resolvedSeedPath = resolveSeedFilePath(seedFile);
        log.info("{} loading seed file. configured={}, resolved={}", LOG_PREFIX, seedFile, resolvedSeedPath);
        if (!seedResource.exists()) {
            String message = "Document bootstrap seed file does not exist: " + seedFile;
            if (failFast) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
            return new BootstrapRunSummary(0, 0, 0, List.of());
        }
        List<BootstrapSeedItem> items = readSeedItems(seedResource);
        Path seedBaseDirectory = resolveSeedBaseDirectory(seedResource);
        log.info(
                "{} parsed seed file. resolved={}, itemCount={}, seedBaseDirectory={}",
                LOG_PREFIX,
                resolvedSeedPath,
                items.size(),
                seedBaseDirectory
        );
        if (items.isEmpty()) {
            log.info("Document bootstrap seed is empty, skip import. seedFile={}", seedFile);
            return new BootstrapRunSummary(0, 0, 0, List.of());
        }

        String operatorUsername = properties.getDocument().getBootstrap().getOperatorUsername();
        if (!StringUtils.hasText(operatorUsername)) {
            operatorUsername = properties.getAdmin().getUsername();
        }
        Long operatorId = adminOperatorService.resolveOperatorId(operatorUsername);

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            BootstrapSeedItem item = items.get(index);
            try {
                String importKey = requireImportKey(item, index);
                if (alreadyImported(importKey)) {
                    skipped++;
                    log.info("{} skip seed item because importKey already exists. importKey={}", LOG_PREFIX, importKey);
                    continue;
                }
                String markdown = resolveMarkdown(item, seedBaseDirectory);
                String extensionJson = resolveExtensionJson(item, importKey);
                String title = requireText(item.title(), "title", index);
                String sourceFilename = requireText(item.sourceFilename(), "sourceFilename", index);
                DocumentVisibilityType visibilityType = resolveVisibility(item.visibilityType());

                documentGovernanceService.createUploadReview(
                        new CreateDocumentReviewRequest(
                                title,
                                sourceFilename,
                                visibilityType,
                                markdown,
                                extensionJson
                        ),
                        operatorId
                );
                created++;
                log.info(
                        "{} created review request. importKey={}, title={}, sourceFilename={}, visibility={}",
                        LOG_PREFIX,
                        importKey,
                        title,
                        sourceFilename,
                        visibilityType
                );
            } catch (Exception exception) {
                failed++;
                String message = "Seed item import failed at index " + index + ": " + exception.getMessage();
                log.error(message, exception);
                errors.add(Map.of("index", String.valueOf(index), "message", message));
                if (failFast) {
                    throw new IllegalStateException(message, exception);
                }
            }
        }
        BootstrapRunSummary summary = new BootstrapRunSummary(created, skipped, failed, errors);
        log.info(
                "{} seed file import finished. resolved={}, created={}, skipped={}, failed={}",
                LOG_PREFIX,
                resolvedSeedPath,
                summary.createdCount(),
                summary.skippedCount(),
                summary.failedCount()
        );
        return summary;
    }

    BootstrapRunSummary importFromSeedDirectory(
            String seedDirectory,
            String seedDirectoryPattern,
            boolean recursive,
            boolean failFast,
            Set<Path> ignoredSeedPaths
    ) {
        Path directoryPath = resolveSeedDirectoryPath(seedDirectory);
        log.info(
                "{} scanning seed directory. configured={}, resolved={}, pattern={}, recursive={}",
                LOG_PREFIX,
                seedDirectory,
                directoryPath,
                seedDirectoryPattern,
                recursive
        );
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            String message = "Document bootstrap seed directory does not exist or is not a directory: " + directoryPath;
            if (failFast) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
            return new BootstrapRunSummary(0, 0, 0, List.of());
        }

        String globPattern = StringUtils.hasText(seedDirectoryPattern) ? seedDirectoryPattern.trim() : "*.json";
        var pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        List<Path> seedFiles = listSeedFiles(directoryPath, maxDepth, pathMatcher);
        log.info(
                "{} scan result. resolvedSeedDirectory={}, matchedSeedFiles={}, files={}",
                LOG_PREFIX,
                directoryPath,
                seedFiles.size(),
                seedFiles.stream().map(Path::toString).toList()
        );
        if (seedFiles.isEmpty()) {
            log.info("No seed file matched in seed directory, skip import. seedDirectory={}, pattern={}", directoryPath, globPattern);
            return new BootstrapRunSummary(0, 0, 0, List.of());
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (Path seedFile : seedFiles) {
            Path normalized = seedFile.toAbsolutePath().normalize();
            if (ignoredSeedPaths.contains(normalized)) {
                continue;
            }
            BootstrapRunSummary summary = importFromSeedFile(normalized.toString(), failFast);
            created += summary.createdCount();
            skipped += summary.skippedCount();
            failed += summary.failedCount();
            errors.addAll(summary.errors());
        }
        BootstrapRunSummary summary = new BootstrapRunSummary(created, skipped, failed, errors);
        log.info(
                "{} seed directory import finished. resolvedSeedDirectory={}, created={}, skipped={}, failed={}",
                LOG_PREFIX,
                directoryPath,
                summary.createdCount(),
                summary.skippedCount(),
                summary.failedCount()
        );
        return summary;
    }

    private boolean alreadyImported(String importKey) {
        return documentReviewRequestRepository.existsByImportKey(importKey)
                || knowledgeDocumentRepository.existsByImportKey(importKey);
    }

    private Resource resolveSeedResource(String seedFile) {
        String trimmed = seedFile.trim();
        if (trimmed.startsWith("classpath:") || trimmed.startsWith("file:")) {
            return resourceLoader.getResource(trimmed);
        }
        String absolute = Path.of(trimmed).toAbsolutePath().toString();
        return resourceLoader.getResource("file:" + absolute);
    }

    private Path resolveSeedFilePath(String seedFile) {
        try {
            return resolveSeedResource(seedFile).getFile().toPath().toAbsolutePath().normalize();
        } catch (IOException exception) {
            return null;
        }
    }

    private Path resolveSeedDirectoryPath(String seedDirectory) {
        String trimmed = seedDirectory.trim();
        if (trimmed.startsWith("classpath:")) {
            throw new IllegalArgumentException("seed-directory does not support classpath resources: " + seedDirectory);
        }
        String normalized = trimmed.startsWith("file:") ? trimmed.substring("file:".length()) : trimmed;
        return Path.of(normalized).toAbsolutePath().normalize();
    }

    private List<Path> listSeedFiles(Path directoryPath, int maxDepth, java.nio.file.PathMatcher pathMatcher) {
        try (var stream = Files.walk(directoryPath, maxDepth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .filter(path -> {
                        Path relativePath = directoryPath.relativize(path);
                        return pathMatcher.matches(relativePath) || pathMatcher.matches(path.getFileName());
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan seed directory: " + directoryPath, exception);
        }
    }

    private List<BootstrapSeedItem> readSeedItems(Resource seedResource) {
        try (var stream = seedResource.getInputStream()) {
            return objectMapper.readValue(stream, SEED_ITEM_LIST_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read bootstrap seed file: " + seedResource, exception);
        }
    }

    private Path resolveSeedBaseDirectory(Resource seedResource) {
        try {
            return seedResource.getFile().toPath().toAbsolutePath().normalize().getParent();
        } catch (IOException exception) {
            return null;
        }
    }

    private String requireImportKey(BootstrapSeedItem item, int index) {
        return requireText(item.importKey(), "importKey", index);
    }

    private String requireText(String value, String fieldName, int index) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing field `" + fieldName + "` at seed index " + index);
        }
        return value.trim();
    }

    private DocumentVisibilityType resolveVisibility(String visibilityType) {
        if (!StringUtils.hasText(visibilityType)) {
            return DocumentVisibilityType.PUBLIC;
        }
        return DocumentVisibilityType.valueOf(visibilityType.trim().toUpperCase(Locale.ROOT));
    }

    private String resolveMarkdown(BootstrapSeedItem item, Path seedBaseDirectory) {
        if (StringUtils.hasText(item.sourceMarkdown())) {
            return item.sourceMarkdown();
        }
        if (!StringUtils.hasText(item.sourceMarkdownPath())) {
            throw new IllegalArgumentException("Either sourceMarkdown or sourceMarkdownPath must be provided");
        }
        Path configuredPath = Path.of(item.sourceMarkdownPath().trim());
        Set<Path> candidates = new LinkedHashSet<>();
        if (configuredPath.isAbsolute()) {
            candidates.add(configuredPath.normalize());
        } else {
            candidates.add(configuredPath.toAbsolutePath().normalize());
            Path ancestor = seedBaseDirectory;
            while (ancestor != null) {
                candidates.add(ancestor.resolve(configuredPath).normalize());
                ancestor = ancestor.getParent();
            }
        }
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read sourceMarkdownPath: " + candidate, exception);
            }
        }
        throw new IllegalStateException("Failed to read sourceMarkdownPath from candidates: " + candidates);
    }

    private String resolveExtensionJson(BootstrapSeedItem item, String importKey) {
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("importKey", importKey);
        extension.put("bootstrapImportedAt", OffsetDateTime.now().toString());
        if (item.extensionJson() != null) {
            if (!(item.extensionJson() instanceof Map<?, ?> mapValue)) {
                throw new IllegalArgumentException("extensionJson must be a JSON object");
            }
            mapValue.forEach((key, value) -> extension.put(String.valueOf(key), value));
            extension.put("importKey", importKey);
        }
        try {
            return objectMapper.writeValueAsString(extension);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize extensionJson", exception);
        }
    }

    record BootstrapSeedItem(
            String importKey,
            String title,
            String sourceFilename,
            String visibilityType,
            String sourceMarkdownPath,
            String sourceMarkdown,
            Object extensionJson
    ) {
    }

    record BootstrapRunSummary(
            int createdCount,
            int skippedCount,
            int failedCount,
            List<Map<String, String>> errors
    ) {
    }
}
