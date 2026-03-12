package com.knowledgebox.domain.agent;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "model_catalog")
public class ModelCatalog extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 16)
    private ModelType modelType;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "public_selectable", nullable = false)
    private Boolean publicSelectable = Boolean.FALSE;

    @Column(name = "default_for_public", nullable = false)
    private Boolean defaultForPublic = Boolean.FALSE;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public void setModelType(ModelType modelType) {
        this.modelType = modelType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getPublicSelectable() {
        return publicSelectable;
    }

    public void setPublicSelectable(Boolean publicSelectable) {
        this.publicSelectable = publicSelectable;
    }

    public Boolean getDefaultForPublic() {
        return defaultForPublic;
    }

    public void setDefaultForPublic(Boolean defaultForPublic) {
        this.defaultForPublic = defaultForPublic;
    }
}
