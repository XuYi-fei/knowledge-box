package com.knowledgebox.service.admin;

import com.knowledgebox.config.KnowledgeBoxProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentBootstrapImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentBootstrapImportRunner.class);
    private static final String LOG_PREFIX = "[AGENT-BOOTSTRAP]";

    private final KnowledgeBoxProperties properties;
    private final ResourceLoader resourceLoader;
    private final AgentConfigAdminService agentConfigAdminService;

    public AgentBootstrapImportRunner(
            KnowledgeBoxProperties properties,
            ResourceLoader resourceLoader,
            AgentConfigAdminService agentConfigAdminService
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.agentConfigAdminService = agentConfigAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeBoxProperties.Bootstrap bootstrap = properties.getAgent().getBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        log.info(
                "{} ===== import start ===== cwd={}, seedFile={}, seedDirectory={}, pattern={}, recursive={}, failFast={}",
                LOG_PREFIX,
                Path.of("").toAbsolutePath().normalize(),
                bootstrap.getSeedFile(),
                bootstrap.getSeedDirectory(),
                bootstrap.getSeedDirectoryPattern(),
                bootstrap.isSeedDirectoryRecursive(),
                bootstrap.isFailFast()
        );
        if (!StringUtils.hasText(bootstrap.getSeedFile()) && !StringUtils.hasText(bootstrap.getSeedDirectory())) {
            log.warn("{} Agent bootstrap is enabled but both seed-file and seed-directory are blank, skip import.", LOG_PREFIX);
            return;
        }
        BootstrapSummary summary = importConfiguredSeeds(bootstrap);
        log.info(
                "{} ===== import completed ===== created={}, skipped={}, failed={}",
                LOG_PREFIX,
                summary.createdCount(),
                summary.skippedCount(),
                summary.failedCount()
        );
        if (!summary.messages().isEmpty()) {
            for (String message : summary.messages()) {
                log.info("{} {}", LOG_PREFIX, message);
            }
        }
    }

    BootstrapSummary importConfiguredSeeds(KnowledgeBoxProperties.Bootstrap bootstrap) {
        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();
        Set<Path> importedFiles = new LinkedHashSet<>();

        if (StringUtils.hasText(bootstrap.getSeedFile())) {
            Path resolved = resolveSeedFilePath(bootstrap.getSeedFile());
            BootstrapSummary summary = importSingleSeed(bootstrap.getSeedFile(), bootstrap.isFailFast());
            created += summary.createdCount();
            skipped += summary.skippedCount();
            failed += summary.failedCount();
            messages.addAll(summary.messages());
            if (resolved != null) {
                importedFiles.add(resolved);
            }
        }

        if (StringUtils.hasText(bootstrap.getSeedDirectory())) {
            Path seedDirectory = resolveSeedDirectoryPath(bootstrap.getSeedDirectory());
            if (!Files.isDirectory(seedDirectory)) {
                String message = "Agent bootstrap seed directory does not exist: " + seedDirectory;
                if (bootstrap.isFailFast()) {
                    throw new IllegalStateException(message);
                }
                log.warn("{} {}", LOG_PREFIX, message);
                return new BootstrapSummary(created, skipped, failed + 1, messages);
            }
            int maxDepth = bootstrap.isSeedDirectoryRecursive() ? Integer.MAX_VALUE : 1;
            String pattern = StringUtils.hasText(bootstrap.getSeedDirectoryPattern())
                    ? bootstrap.getSeedDirectoryPattern().trim()
                    : "*.json";
            try (var stream = Files.walk(seedDirectory, maxDepth)) {
                List<Path> seedFiles = stream
                        .filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .filter(path -> FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(path.getFileName()))
                        .sorted()
                        .toList();
                for (Path seedFile : seedFiles) {
                    if (importedFiles.contains(seedFile)) {
                        continue;
                    }
                    BootstrapSummary summary = importSingleSeed(seedFile.toString(), bootstrap.isFailFast());
                    created += summary.createdCount();
                    skipped += summary.skippedCount();
                    failed += summary.failedCount();
                    messages.addAll(summary.messages());
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to scan agent bootstrap seed directory: " + seedDirectory, exception);
            }
        }

        return new BootstrapSummary(created, skipped, failed, List.copyOf(messages));
    }

    private BootstrapSummary importSingleSeed(String location, boolean failFast) {
        Resource resource = resolveResource(location);
        if (!resource.exists()) {
            String message = "Agent bootstrap seed file does not exist: " + location;
            if (failFast) {
                throw new IllegalStateException(message);
            }
            log.warn("{} {}", LOG_PREFIX, message);
            return new BootstrapSummary(0, 1, 0, List.of(message));
        }
        try (InputStream inputStream = resource.getInputStream()) {
            AgentConfigAdminService.BootstrapImportResult result = agentConfigAdminService.importForBootstrap(
                    inputStream,
                    location,
                    failFast
            );
            return new BootstrapSummary(
                    result.createdCount(),
                    result.skippedCount(),
                    result.failedCount(),
                    result.messages()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read agent bootstrap seed file: " + location, exception);
        }
    }

    private Resource resolveResource(String location) {
        String trimmed = location.trim();
        if (trimmed.startsWith("classpath:") || trimmed.startsWith("file:")) {
            return resourceLoader.getResource(trimmed);
        }
        return resourceLoader.getResource("file:" + Path.of(trimmed).toAbsolutePath().normalize());
    }

    private Path resolveSeedFilePath(String location) {
        try {
            return resolveResource(location).getFile().toPath().toAbsolutePath().normalize();
        } catch (IOException exception) {
            return null;
        }
    }

    private Path resolveSeedDirectoryPath(String location) {
        String trimmed = location.trim();
        if (trimmed.startsWith("classpath:")) {
            throw new IllegalArgumentException("agent bootstrap seed-directory does not support classpath resources: " + location);
        }
        if (trimmed.startsWith("file:")) {
            return Path.of(trimmed.substring("file:".length())).toAbsolutePath().normalize();
        }
        return Path.of(trimmed).toAbsolutePath().normalize();
    }

    record BootstrapSummary(int createdCount, int skippedCount, int failedCount, List<String> messages) {
    }
}
