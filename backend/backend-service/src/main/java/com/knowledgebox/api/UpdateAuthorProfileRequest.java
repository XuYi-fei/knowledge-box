package com.knowledgebox.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateAuthorProfileRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 32) String gender,
        @Size(max = 128) String email,
        @Size(max = 64) String phone,
        @Min(0) @Max(150) Integer age,
        @Valid List<EducationItem> educations,
        @Valid List<SkillItem> skills,
        @Valid List<ExperienceItem> workExperiences,
        @Valid List<ExperienceItem> internshipExperiences,
        @Valid List<ExperienceItem> projectExperiences,
        @Valid List<CustomSection> customSections
) {

    public UpdateAuthorProfileRequest {
        educations = educations == null ? List.of() : List.copyOf(educations);
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
        internshipExperiences = internshipExperiences == null ? List.of() : List.copyOf(internshipExperiences);
        projectExperiences = projectExperiences == null ? List.of() : List.copyOf(projectExperiences);
        customSections = customSections == null ? List.of() : List.copyOf(customSections);
    }

    public record EducationItem(
            @Size(max = 32) String stageLabel,
            @NotBlank @Size(max = 256) String schoolName,
            @Size(max = 128) String periodText,
            @Size(max = 128) String major,
            List<@Size(max = 256) String> honors
    ) {

        public EducationItem {
            honors = honors == null ? List.of() : List.copyOf(honors);
        }
    }

    public record SkillItem(
            @NotBlank @Size(max = 128) String label,
            @Size(max = 20000) String descriptionMarkdown
    ) {
    }

    public record ExperienceItem(
            @NotBlank @Size(max = 256) String name,
            @Size(max = 128) String periodText,
            @Size(max = 20000) String summaryMarkdown,
            List<@Size(max = 4000) String> responsibilityItems,
            List<@Size(max = 64) String> techStacks
    ) {

        public ExperienceItem {
            responsibilityItems = responsibilityItems == null ? List.of() : List.copyOf(responsibilityItems);
            techStacks = techStacks == null ? List.of() : List.copyOf(techStacks);
        }
    }

    public record CustomSection(
            @Size(max = 128) String sectionTitle,
            @Valid List<CustomSectionItem> items
    ) {

        public CustomSection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record CustomSectionItem(
            @Size(max = 128) String itemTitle,
            @Size(max = 128) String periodText,
            @Size(max = 20000) String descriptionMarkdown
    ) {
    }
}
