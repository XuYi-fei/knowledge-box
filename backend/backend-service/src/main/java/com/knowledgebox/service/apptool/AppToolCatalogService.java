package com.knowledgebox.service.apptool;

import com.knowledgebox.api.AppToolCatalogItemView;
import com.knowledgebox.domain.apptool.AppToolDefinition;
import com.knowledgebox.repository.AppToolDefinitionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppToolCatalogService {

    private final AppToolDefinitionRepository definitionRepository;
    private final AppToolSchemaSupport schemaSupport;

    public AppToolCatalogService(
            AppToolDefinitionRepository definitionRepository,
            AppToolSchemaSupport schemaSupport
    ) {
        this.definitionRepository = definitionRepository;
        this.schemaSupport = schemaSupport;
    }

    @Transactional(readOnly = true)
    public List<AppToolCatalogItemView> catalog() {
        return definitionRepository.findAllByEnabledTrueOrderByDisplayOrderAscNameAscIdAsc().stream()
                .map(this::toCatalogItem)
                .toList();
    }

    private AppToolCatalogItemView toCatalogItem(AppToolDefinition definition) {
        return new AppToolCatalogItemView(
                definition.getCode(),
                definition.getName(),
                definition.getSummary(),
                definition.getDescriptionMarkdown(),
                definition.getCategoryCode(),
                definition.getIconKey(),
                schemaSupport.tagsFromJson(definition.getTagsJson()),
                definition.getDisplayOrder(),
                definition.getExecutionMode().name(),
                definition.getRendererCode(),
                definition.getHandlerCode(),
                definition.getInputSchemaJson(),
                definition.getDefaultValuesJson(),
                definition.getResultSchemaJson()
        );
    }
}
