package com.knowledgebox.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knowledgebox.common.BaseEntity;
import com.knowledgebox.domain.document.KnowledgeIngestionDraft;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftSourceType;
import com.knowledgebox.repository.KnowledgeIngestionDraftRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeIngestionSourceToolTests {

    private KnowledgeIngestionDraftRepository draftRepository;
    private KnowledgeIngestionSourceTool sourceTool;

    @BeforeEach
    void setUp() {
        draftRepository = mock(KnowledgeIngestionDraftRepository.class);
        sourceTool = new KnowledgeIngestionSourceTool(draftRepository);
    }

    @Test
    void shouldReadMarkdownSourceContent() throws Exception {
        KnowledgeIngestionDraft draft = draft(11L, KnowledgeIngestionDraftSourceType.MARKDOWN, "# Redis\n\nIO 多路复用");
        when(draftRepository.findById(11L)).thenReturn(Optional.of(draft));

        String content = sourceTool.readMarkdownSource(11L);

        assertThat(content).isEqualTo("# Redis\n\nIO 多路复用");
    }

    @Test
    void shouldReadPdfExtractedText() throws Exception {
        KnowledgeIngestionDraft draft = draft(12L, KnowledgeIngestionDraftSourceType.PDF, "Redis 使用 IO 多路复用处理连接事件。");
        when(draftRepository.findById(12L)).thenReturn(Optional.of(draft));

        String content = sourceTool.readPdfSource(12L);

        assertThat(content).contains("IO 多路复用");
    }

    @Test
    void shouldRejectMismatchedSourceType() throws Exception {
        KnowledgeIngestionDraft draft = draft(13L, KnowledgeIngestionDraftSourceType.PDF, "pdf text");
        when(draftRepository.findById(13L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> sourceTool.readMarkdownSource(13L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a MARKDOWN source");
    }

    private KnowledgeIngestionDraft draft(Long id, KnowledgeIngestionDraftSourceType sourceType, String sourceContent) throws Exception {
        KnowledgeIngestionDraft draft = new KnowledgeIngestionDraft();
        Field idField = BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(draft, id);
        draft.setSourceType(sourceType);
        draft.setSourceContent(sourceContent);
        return draft;
    }
}
