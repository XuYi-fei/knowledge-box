package com.knowledgebox.repository;

import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelCatalogRepository extends JpaRepository<ModelCatalog, Long> {

    boolean existsByCode(String code);

    Optional<ModelCatalog> findByCode(String code);

    Optional<ModelCatalog> findByCodeAndModelTypeAndEnabledTrue(String code, ModelType modelType);

    Optional<ModelCatalog> findByCodeAndModelTypeAndEnabledTrueAndPublicSelectableTrue(String code, ModelType modelType);

    List<ModelCatalog> findAllByOrderByModelTypeAscDisplayNameAsc();

    List<ModelCatalog> findAllByModelTypeAndEnabledTrueOrderByDisplayNameAsc(ModelType modelType);

    List<ModelCatalog> findAllByModelTypeAndEnabledTrueAndPublicSelectableTrueOrderByDisplayNameAsc(ModelType modelType);

    List<ModelCatalog> findAllByModelTypeAndDefaultForPublicTrue(ModelType modelType);
}
