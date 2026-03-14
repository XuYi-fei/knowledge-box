package com.knowledgebox.service.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.domain.document.IngestionJob;
import com.knowledgebox.domain.document.IngestionJobStatus;
import com.knowledgebox.domain.document.KnowledgeDocument;
import com.knowledgebox.repository.DocumentChunkRepository;
import com.knowledgebox.repository.IngestionJobRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.service.chat.KnowledgeBaseIndexingService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");

    private final StorageService storageService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final KnowledgeBaseIndexingService knowledgeBaseIndexingService;
    private final KnowledgeBoxProperties properties;
    private final ObjectMapper objectMapper;

    public DocumentIngestionService(
            StorageService storageService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            DocumentChunkRepository documentChunkRepository,
            IngestionJobRepository ingestionJobRepository,
            KnowledgeBaseIndexingService knowledgeBaseIndexingService,
            KnowledgeBoxProperties properties,
            ObjectMapper objectMapper
    ) {
        this.storageService = storageService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.knowledgeBaseIndexingService = knowledgeBaseIndexingService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentUploadResult ingest(MultipartFile markdownFile, List<MultipartFile> assetFiles) {
        String markdown = readMarkdown(markdownFile);
        Map<String, StorageService.StoredObject> uploadedAssets = uploadAssets(assetFiles);
        String normalizedMarkdown = rewriteAssetReferences(markdown, uploadedAssets);
        String normalizedPath = writeNormalizedMarkdown(markdownFile, normalizedMarkdown);
        String title = deriveTitle(markdownFile.getOriginalFilename(), markdown);

        KnowledgeDocument document = saveDocument(title, markdownFile.getOriginalFilename(), normalizedPath);
        List<DocumentChunk> chunks = documentChunkRepository.saveAll(splitMarkdown(document, normalizedMarkdown));
        saveIngestionJob(document, chunks.size(), uploadedAssets.size());
        knowledgeBaseIndexingService.index(chunks);

        return new DocumentUploadResult(
                title,
                markdownFile.getOriginalFilename(),
                normalizedPath,
                uploadedAssets.values().stream().map(StorageService.StoredObject::url).toList(),
                null,
                null
        );
    }

    private String readMarkdown(MultipartFile markdownFile) {
        try {
            return new String(markdownFile.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read markdown file", exception);
        }
    }

    private Map<String, StorageService.StoredObject> uploadAssets(List<MultipartFile> assetFiles) {
        Map<String, StorageService.StoredObject> assetMap = new HashMap<>();
        for (MultipartFile assetFile : assetFiles) {
            if (assetFile.isEmpty()) {
                continue;
            }
            String originalFilename = StringUtils.cleanPath(assetFile.getOriginalFilename() == null ? "" : assetFile.getOriginalFilename());
            assetMap.put(originalFilename, storageService.store("assets", assetFile));
        }
        return assetMap;
    }

    private String rewriteAssetReferences(String markdown, Map<String, StorageService.StoredObject> assetMap) {
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String originalPath = matcher.group(2).trim();
            String fileName = Path.of(originalPath).getFileName().toString();
            String replacementPath = assetMap.containsKey(fileName) ? assetMap.get(fileName).url() : originalPath;
            String replacement = "![" + matcher.group(1) + "](" + replacementPath + ")";
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String writeNormalizedMarkdown(MultipartFile markdownFile, String normalizedMarkdown) {
        String originalName = StringUtils.cleanPath(markdownFile.getOriginalFilename() == null ? "document.md" : markdownFile.getOriginalFilename());
        String fileName = originalName.endsWith(".md") ? originalName.substring(0, originalName.length() - 3) : originalName;
        Path target = Path.of("backend/uploads/docs").resolve(fileName + ".normalized.md");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, normalizedMarkdown, StandardCharsets.UTF_8);
            return "/uploads/docs/" + target.getFileName();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write normalized markdown", exception);
        }
    }

    private String deriveTitle(String originalFilename, String markdown) {
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        String fallback = StringUtils.hasText(originalFilename) ? originalFilename : "Untitled Document";
        return fallback.replace(".md", "");
    }

    private KnowledgeDocument saveDocument(String title, String sourceFilename, String normalizedPath) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title);
        document.setSourceFilename(sourceFilename == null ? "document.md" : sourceFilename);
        document.setStatus(DocumentStatus.READY);
        document.setUploaderType(DocumentUploaderType.ADMIN);
        document.setVisibilityType(DocumentVisibilityType.PUBLIC);
        document.setNormalizedMarkdownPath(normalizedPath);
        document.setSourceMarkdown("");
        document.setExtensionJson("{}");
        document.setVectorConfigJson("{}");
        document.setTags("[]");
        return knowledgeDocumentRepository.save(document);
    }

    private void saveIngestionJob(KnowledgeDocument document, int chunkCount, int assetCount) {
        IngestionJob job = new IngestionJob();
        job.setDocument(document);
        job.setStatus(IngestionJobStatus.COMPLETED);
        job.setJobType("MARKDOWN_INGESTION");
        job.setDetail(writeJson(Map.of("chunks", chunkCount, "assets", assetCount)));
        ingestionJobRepository.save(job);
    }

    private List<DocumentChunk> splitMarkdown(KnowledgeDocument document, String markdown) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;

        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.stripTrailing();
            if (line.matches("^#{1,6}\\s+.*")) {
                chunkIndex = flushChunk(chunks, document, headingStack, buffer, chunkIndex);
                updateHeadingStack(headingStack, line);
                buffer.append(line).append('\n');
                continue;
            }

            if (buffer.length() + line.length() + 1 > properties.getRetrieval().getChunkSize()) {
                chunkIndex = flushChunk(chunks, document, headingStack, buffer, chunkIndex);
            }
            buffer.append(line).append('\n');
        }

        flushChunk(chunks, document, headingStack, buffer, chunkIndex);
        return chunks;
    }

    private int flushChunk(
            List<DocumentChunk> chunks,
            KnowledgeDocument document,
            List<String> headingStack,
            StringBuilder buffer,
            int chunkIndex
    ) {
        String content = buffer.toString().trim();
        if (!StringUtils.hasText(content)) {
            buffer.setLength(0);
            return chunkIndex;
        }

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(chunkIndex);
        chunk.setHeadingPath(headingStack.isEmpty() ? "未分节" : String.join(" / ", headingStack));
        chunk.setAnchor(anchorFor(headingStack, chunkIndex));
        chunk.setContent(content);
        chunk.setMetadataJson(writeJson(Map.of(
                "documentTitle", document.getTitle(),
                "headingPath", chunk.getHeadingPath(),
                "chunkIndex", chunkIndex
        )));
        chunks.add(chunk);
        buffer.setLength(0);
        return chunkIndex + 1;
    }

    private void updateHeadingStack(List<String> headingStack, String headingLine) {
        int level = 0;
        while (level < headingLine.length() && headingLine.charAt(level) == '#') {
            level++;
        }

        String heading = headingLine.substring(level).trim();
        while (headingStack.size() >= level) {
            headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(heading);
    }

    private String anchorFor(List<String> headingStack, int chunkIndex) {
        String base = headingStack.isEmpty() ? "chunk" : headingStack.get(headingStack.size() - 1);
        String normalized = base.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("(^-|-$)", "");
        return (normalized.isBlank() ? "chunk" : normalized) + "-" + chunkIndex;
    }

    private String writeJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize ingestion metadata", exception);
        }
    }
}
