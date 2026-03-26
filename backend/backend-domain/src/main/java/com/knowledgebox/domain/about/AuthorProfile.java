package com.knowledgebox.domain.about;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "author_profile")
public class AuthorProfile extends BaseEntity {

    @Column(name = "profile_key", nullable = false, unique = true, length = 64)
    private String profileKey;

    @Column(length = 128)
    private String name;

    @Column(length = 32)
    private String gender;

    @Column(length = 128)
    private String email;

    @Column(length = 64)
    private String phone;

    @Column
    private Integer age;

    @Column(name = "photo_provider", length = 32)
    private String photoProvider;

    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(name = "photo_content_type", length = 128)
    private String photoContentType;

    @Column(name = "photo_content_length")
    private Long photoContentLength;

    @Column(name = "education_json", columnDefinition = "TEXT")
    private String educationJson;

    @Column(name = "skill_json", columnDefinition = "TEXT")
    private String skillJson;

    @Column(name = "work_experience_json", columnDefinition = "TEXT")
    private String workExperienceJson;

    @Column(name = "internship_experience_json", columnDefinition = "TEXT")
    private String internshipExperienceJson;

    @Column(name = "project_experience_json", columnDefinition = "TEXT")
    private String projectExperienceJson;

    @Column(name = "custom_section_json", columnDefinition = "TEXT")
    private String customSectionJson;

    public String getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(String profileKey) {
        this.profileKey = profileKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getPhotoProvider() {
        return photoProvider;
    }

    public void setPhotoProvider(String photoProvider) {
        this.photoProvider = photoProvider;
    }

    public String getPhotoObjectKey() {
        return photoObjectKey;
    }

    public void setPhotoObjectKey(String photoObjectKey) {
        this.photoObjectKey = photoObjectKey;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPhotoContentType() {
        return photoContentType;
    }

    public void setPhotoContentType(String photoContentType) {
        this.photoContentType = photoContentType;
    }

    public Long getPhotoContentLength() {
        return photoContentLength;
    }

    public void setPhotoContentLength(Long photoContentLength) {
        this.photoContentLength = photoContentLength;
    }

    public String getEducationJson() {
        return educationJson;
    }

    public void setEducationJson(String educationJson) {
        this.educationJson = educationJson;
    }

    public String getSkillJson() {
        return skillJson;
    }

    public void setSkillJson(String skillJson) {
        this.skillJson = skillJson;
    }

    public String getWorkExperienceJson() {
        return workExperienceJson;
    }

    public void setWorkExperienceJson(String workExperienceJson) {
        this.workExperienceJson = workExperienceJson;
    }

    public String getInternshipExperienceJson() {
        return internshipExperienceJson;
    }

    public void setInternshipExperienceJson(String internshipExperienceJson) {
        this.internshipExperienceJson = internshipExperienceJson;
    }

    public String getProjectExperienceJson() {
        return projectExperienceJson;
    }

    public void setProjectExperienceJson(String projectExperienceJson) {
        this.projectExperienceJson = projectExperienceJson;
    }

    public String getCustomSectionJson() {
        return customSectionJson;
    }

    public void setCustomSectionJson(String customSectionJson) {
        this.customSectionJson = customSectionJson;
    }
}
