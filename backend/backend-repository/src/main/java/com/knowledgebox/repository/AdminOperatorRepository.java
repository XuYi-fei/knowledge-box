package com.knowledgebox.repository;

import com.knowledgebox.domain.admin.AdminOperator;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminOperatorRepository extends JpaRepository<AdminOperator, Long> {

    Optional<AdminOperator> findByUsername(String username);
}
