package com.knowledgebox.service.about;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AuthorProfilePhotoUploadView;
import com.knowledgebox.api.AuthorProfileView;
import com.knowledgebox.api.UpdateAuthorProfileRequest;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.about.AuthorProfile;
import com.knowledgebox.repository.AuthorProfileRepository;
import com.knowledgebox.service.document.StorageService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AuthorProfileAdminServiceTests {

    private AuthorProfileRepository authorProfileRepository;
    private StorageService storageService;
    private AuthorProfileAdminService service;

    @BeforeEach
    void setUp() {
        authorProfileRepository = mock(AuthorProfileRepository.class);
        storageService = mock(StorageService.class);
        service = new AuthorProfileAdminService(authorProfileRepository, storageService, new ObjectMapper());
    }

    @Test
    void shouldReturnEmptyViewWhenProfileDoesNotExist() {
        when(authorProfileRepository.findByProfileKey("default")).thenReturn(Optional.empty());

        AuthorProfileView view = service.publicProfile();

        assertThat(view.configured()).isFalse();
        assertThat(view.name()).isNull();
        assertThat(view.educations()).isEmpty();
        assertThat(view.customSections()).isEmpty();
    }

    @Test
    void shouldPersistStructuredProfileAndReturnNormalizedView() {
        when(authorProfileRepository.findByProfileKey("default")).thenReturn(Optional.empty());
        when(authorProfileRepository.save(any(AuthorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAuthorProfileRequest request = new UpdateAuthorProfileRequest(
                "  徐逸飞  ",
                "男",
                "author@example.com",
                "123456789",
                29,
                List.of(new UpdateAuthorProfileRequest.EducationItem(
                        "本科",
                        "某大学",
                        "2015.09 - 2019.06",
                        "计算机科学与技术",
                        List.of("国家奖学金", "优秀毕业生")
                )),
                List.of(new UpdateAuthorProfileRequest.SkillItem("Java 后端", "**Spring Boot** 与 PostgreSQL")),
                List.of(new UpdateAuthorProfileRequest.ExperienceItem(
                        "某公司",
                        "2023.01 - 2024.01",
                        "负责核心系统迭代",
                        List.of("负责 **多租户** 权限改造", "重构查询链路"),
                        List.of("Spring Boot", "PostgreSQL")
                )),
                List.of(),
                List.of(),
                List.of(new UpdateAuthorProfileRequest.CustomSection(
                        "竞赛经历",
                        List.of(new UpdateAuthorProfileRequest.CustomSectionItem(
                                "算法竞赛",
                                "2018",
                                "获得 **省一等奖**"
                        ))
                ))
        );

        AuthorProfileView view = service.updateProfile(request);

        assertThat(view.configured()).isTrue();
        assertThat(view.name()).isEqualTo("徐逸飞");
        assertThat(view.educations()).hasSize(1);
        assertThat(view.skills()).hasSize(1);
        assertThat(view.workExperiences()).hasSize(1);
        assertThat(view.customSections()).hasSize(1);
        assertThat(view.workExperiences().get(0).responsibilityItems()).contains("负责 **多租户** 权限改造");
        verify(authorProfileRepository).save(any(AuthorProfile.class));
    }

    @Test
    void shouldUploadPhotoAndDeletePreviousObject() {
        AuthorProfile existing = new AuthorProfile();
        existing.setProfileKey("default");
        existing.setPhotoObjectKey("author-profile-photo/old.png");
        when(authorProfileRepository.findByProfileKey("default")).thenReturn(Optional.of(existing));
        when(authorProfileRepository.save(any(AuthorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.store(eq("author-profile-photo"), any())).thenReturn(new StorageService.StoredObject(
                "local",
                "author-profile-photo/new.png",
                "/uploads/author-profile-photo/new.png",
                "image/png",
                128L
        ));

        AuthorProfilePhotoUploadView result = service.uploadPhoto(new MockMultipartFile(
                "image",
                "author.png",
                "image/png",
                "photo".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(result.url()).isEqualTo("/uploads/author-profile-photo/new.png");
        verify(storageService).delete("author-profile-photo/old.png");
    }

    @Test
    void shouldRejectNonImagePhotoUpload() {
        MockMultipartFile file = new MockMultipartFile("image", "author.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadPhoto(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("作者照片必须是图片文件");
    }
}
