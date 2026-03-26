package com.knowledgebox.service.about;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AuthorProfilePhotoUploadView;
import com.knowledgebox.api.AuthorProfileView;
import com.knowledgebox.api.UpdateAuthorProfileRequest;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.about.AuthorProfile;
import com.knowledgebox.repository.AuthorProfileRepository;
import com.knowledgebox.service.document.StorageService;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthorProfileAdminService {

    private static final String DEFAULT_PROFILE_KEY = "default";
    private static final TypeReference<List<AuthorProfileView.EducationItem>> EDUCATION_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AuthorProfileView.SkillItem>> SKILL_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AuthorProfileView.ExperienceItem>> EXPERIENCE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AuthorProfileView.CustomSection>> CUSTOM_SECTION_LIST_TYPE = new TypeReference<>() {
    };

    private final AuthorProfileRepository authorProfileRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public AuthorProfileAdminService(
            AuthorProfileRepository authorProfileRepository,
            StorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.authorProfileRepository = authorProfileRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuthorProfileView profile() {
        return toView(authorProfileRepository.findByProfileKey(DEFAULT_PROFILE_KEY).orElse(null));
    }

    @Transactional
    public AuthorProfileView updateProfile(UpdateAuthorProfileRequest request) {
        AuthorProfile profile = requireOrCreateProfile();
        profile.setName(normalizeText(request.name()));
        profile.setGender(normalizeText(request.gender()));
        profile.setEmail(normalizeText(request.email()));
        profile.setPhone(normalizeText(request.phone()));
        profile.setAge(request.age());
        profile.setEducationJson(writeJson(request.educations().stream()
                .map(item -> new AuthorProfileView.EducationItem(
                        normalizeText(item.stageLabel()),
                        normalizeText(item.schoolName()),
                        normalizeText(item.periodText()),
                        normalizeText(item.major()),
                        normalizeStringList(item.honors())
                ))
                .toList()));
        profile.setSkillJson(writeJson(request.skills().stream()
                .map(item -> new AuthorProfileView.SkillItem(
                        normalizeText(item.label()),
                        normalizeMarkdown(item.descriptionMarkdown())
                ))
                .toList()));
        profile.setWorkExperienceJson(writeJson(toExperienceItems(request.workExperiences())));
        profile.setInternshipExperienceJson(writeJson(toExperienceItems(request.internshipExperiences())));
        profile.setProjectExperienceJson(writeJson(toExperienceItems(request.projectExperiences())));
        profile.setCustomSectionJson(writeJson(request.customSections().stream()
                .map(section -> new AuthorProfileView.CustomSection(
                        normalizeText(section.sectionTitle()),
                        section.items().stream()
                                .map(item -> new AuthorProfileView.CustomSectionItem(
                                        normalizeText(item.itemTitle()),
                                        normalizeText(item.periodText()),
                                        normalizeMarkdown(item.descriptionMarkdown())
                                ))
                                .toList()
                ))
                .toList()));
        return toView(authorProfileRepository.save(profile));
    }

    @Transactional
    public AuthorProfilePhotoUploadView uploadPhoto(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_PHOTO_REQUIRED", "请先上传作者照片");
        }
        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_PHOTO_TYPE_INVALID", "作者照片必须是图片文件");
        }
        String originalFilename = StringUtils.cleanPath(
                StringUtils.hasText(image.getOriginalFilename()) ? image.getOriginalFilename() : "author-photo"
        );
        byte[] bytes = readBytes(image);
        MultipartFile normalizedFile = new InMemoryMultipartFile(originalFilename, contentType, bytes);
        StorageService.StoredObject stored = storageService.store("author-profile-photo", normalizedFile);

        AuthorProfile profile = requireOrCreateProfile();
        String previousObjectKey = profile.getPhotoObjectKey();
        profile.setPhotoProvider(stored.provider());
        profile.setPhotoObjectKey(stored.objectKey());
        profile.setPhotoUrl(stored.url());
        profile.setPhotoContentType(stored.contentType());
        profile.setPhotoContentLength(stored.contentLength());
        authorProfileRepository.save(profile);

        if (StringUtils.hasText(previousObjectKey) && !previousObjectKey.equals(stored.objectKey())) {
            storageService.delete(previousObjectKey);
        }

        return new AuthorProfilePhotoUploadView(
                stored.provider(),
                stored.objectKey(),
                stored.url(),
                stored.contentType(),
                stored.contentLength()
        );
    }

    @Transactional(readOnly = true)
    public AuthorProfileView publicProfile() {
        return toView(authorProfileRepository.findByProfileKey(DEFAULT_PROFILE_KEY).orElse(null));
    }

    private List<AuthorProfileView.ExperienceItem> toExperienceItems(List<UpdateAuthorProfileRequest.ExperienceItem> items) {
        return items.stream()
                .map(item -> new AuthorProfileView.ExperienceItem(
                        normalizeText(item.name()),
                        normalizeText(item.periodText()),
                        normalizeMarkdown(item.summaryMarkdown()),
                        normalizeStringList(item.responsibilityItems()),
                        normalizeStringList(item.techStacks())
                ))
                .toList();
    }

    private AuthorProfile requireOrCreateProfile() {
        return authorProfileRepository.findByProfileKey(DEFAULT_PROFILE_KEY)
                .orElseGet(() -> {
                    AuthorProfile profile = new AuthorProfile();
                    profile.setProfileKey(DEFAULT_PROFILE_KEY);
                    return profile;
                });
    }

    private AuthorProfileView toView(@Nullable AuthorProfile profile) {
        if (profile == null) {
            return new AuthorProfileView(false, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        boolean configured = StringUtils.hasText(profile.getName());
        return new AuthorProfileView(
                configured,
                profile.getName(),
                profile.getGender(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getAge(),
                profile.getPhotoUrl(),
                profile.getPhotoContentType(),
                profile.getPhotoContentLength(),
                readList(profile.getEducationJson(), EDUCATION_LIST_TYPE),
                readList(profile.getSkillJson(), SKILL_LIST_TYPE),
                readList(profile.getWorkExperienceJson(), EXPERIENCE_LIST_TYPE),
                readList(profile.getInternshipExperienceJson(), EXPERIENCE_LIST_TYPE),
                readList(profile.getProjectExperienceJson(), EXPERIENCE_LIST_TYPE),
                readList(profile.getCustomSectionJson(), CUSTOM_SECTION_LIST_TYPE)
        );
    }

    private <T> List<T> readList(String rawJson, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            List<T> parsed = objectMapper.readValue(rawJson, typeReference);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse author profile JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize author profile JSON", exception);
        }
    }

    private List<String> normalizeStringList(List<String> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        return rawItems.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMarkdown(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_PHOTO_READ_FAILED", "无法读取作者照片");
        }
    }

    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] bytes) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }

        @Override
        public void transferTo(java.nio.file.Path dest) throws IOException {
            java.nio.file.Files.write(dest, bytes);
        }

    }
}
