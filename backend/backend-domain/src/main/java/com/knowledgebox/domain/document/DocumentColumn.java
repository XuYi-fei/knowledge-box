package com.knowledgebox.domain.document;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_column")
public class DocumentColumn extends BaseEntity {

    @Column(nullable = false, length = 128, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentTaxonomySource source = DocumentTaxonomySource.SYSTEM;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DocumentTaxonomySource getSource() {
        return source;
    }

    public void setSource(DocumentTaxonomySource source) {
        this.source = source;
    }
}
