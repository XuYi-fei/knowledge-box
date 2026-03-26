package com.knowledgebox.repository;

import com.knowledgebox.domain.about.AuthorProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorProfileRepository extends JpaRepository<AuthorProfile, Long> {

    Optional<AuthorProfile> findByProfileKey(String profileKey);
}
