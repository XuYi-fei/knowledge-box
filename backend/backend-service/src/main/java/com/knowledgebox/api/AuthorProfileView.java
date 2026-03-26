package com.knowledgebox.api;

import java.util.List;

public record AuthorProfileView(
        boolean configured,
        String name,
        String gender,
        String email,
        String phone,
        Integer age,
        String photoUrl,
        String photoContentType,
        Long photoContentLength,
        List<EducationItem> educations,
        List<SkillItem> skills,
        List<ExperienceItem> workExperiences,
        List<ExperienceItem> internshipExperiences,
        List<ExperienceItem> projectExperiences,
        List<CustomSection> customSections
) {

    public AuthorProfileView {
        educations = educations == null ? List.of() : List.copyOf(educations);
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
        internshipExperiences = internshipExperiences == null ? List.of() : List.copyOf(internshipExperiences);
        projectExperiences = projectExperiences == null ? List.of() : List.copyOf(projectExperiences);
        customSections = customSections == null ? List.of() : List.copyOf(customSections);
    }

    public record EducationItem(
            String stageLabel,
            String schoolName,
            String periodText,
            String major,
            List<String> honors
    ) {

        public EducationItem {
            honors = honors == null ? List.of() : List.copyOf(honors);
        }
    }

    public record SkillItem(
            String label,
            String descriptionMarkdown
    ) {
    }

    public record ExperienceItem(
            String name,
            String periodText,
            String summaryMarkdown,
            List<String> responsibilityItems,
            List<String> techStacks
    ) {

        public ExperienceItem {
            responsibilityItems = responsibilityItems == null ? List.of() : List.copyOf(responsibilityItems);
            techStacks = techStacks == null ? List.of() : List.copyOf(techStacks);
        }
    }

    public record CustomSection(
            String sectionTitle,
            List<CustomSectionItem> items
    ) {

        public CustomSection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record CustomSectionItem(
            String itemTitle,
            String periodText,
            String descriptionMarkdown
    ) {
    }
}
