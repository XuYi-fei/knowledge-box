package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.SkillBinding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillBindingRepository extends JpaRepository<SkillBinding, Long> {

    Optional<SkillBinding> findByCode(String code);

    boolean existsByCode(String code);
}
