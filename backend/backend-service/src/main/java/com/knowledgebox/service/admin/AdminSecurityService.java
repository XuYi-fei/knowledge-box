package com.knowledgebox.service.admin;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.admin.AdminOperator;
import com.knowledgebox.repository.AdminOperatorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminSecurityService {

    private final AdminOperatorRepository adminOperatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeBoxProperties properties;

    public AdminSecurityService(
            AdminOperatorRepository adminOperatorRepository,
            PasswordEncoder passwordEncoder,
            KnowledgeBoxProperties properties
    ) {
        this.adminOperatorRepository = adminOperatorRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public UserDetails loadUserDetails(String username) {
        String normalized = normalize(username);
        AdminOperator operator = adminOperatorRepository.findByUsername(normalized)
                .orElseGet(() -> createFromConfiguredAdmin(normalized));
        if (operator == null) {
            throw new UsernameNotFoundException("Admin user not found: " + normalized);
        }
        operator = initializePasswordFromConfigIfNeeded(operator);
        if (!StringUtils.hasText(operator.getPasswordHash())) {
            throw new UsernameNotFoundException("Admin password not initialized: " + normalized);
        }
        return User.withUsername(operator.getUsername())
                .password(operator.getPasswordHash())
                .roles("ADMIN")
                .build();
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        String normalized = normalize(username);
        AdminOperator operator = adminOperatorRepository.findByUsername(normalized)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "管理员不存在"));
        operator = initializePasswordFromConfigIfNeeded(operator);
        if (!StringUtils.hasText(operator.getPasswordHash())
                || !passwordEncoder.matches(currentPassword, operator.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        if (passwordEncoder.matches(newPassword, operator.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        operator.setPasswordHash(passwordEncoder.encode(newPassword));
        adminOperatorRepository.save(operator);
    }

    @Transactional
    public AdminOperator ensureOperator(String username) {
        String normalized = normalize(username);
        AdminOperator operator = adminOperatorRepository.findByUsername(normalized)
                .orElseGet(() -> {
                    AdminOperator created = new AdminOperator();
                    created.setUsername(normalized);
                    return adminOperatorRepository.save(created);
                });
        return initializePasswordFromConfigIfNeeded(operator);
    }

    private AdminOperator createFromConfiguredAdmin(String normalizedUsername) {
        String configuredUsername = normalize(properties.getAdmin().getUsername());
        if (!configuredUsername.equals(normalizedUsername)) {
            return null;
        }
        if (!StringUtils.hasText(properties.getAdmin().getPassword())) {
            return null;
        }
        AdminOperator created = new AdminOperator();
        created.setUsername(configuredUsername);
        created.setPasswordHash(passwordEncoder.encode(properties.getAdmin().getPassword()));
        return adminOperatorRepository.save(created);
    }

    private AdminOperator initializePasswordFromConfigIfNeeded(AdminOperator operator) {
        if (StringUtils.hasText(operator.getPasswordHash())) {
            return operator;
        }
        String configuredUsername = normalize(properties.getAdmin().getUsername());
        if (!configuredUsername.equals(operator.getUsername())) {
            return operator;
        }
        if (!StringUtils.hasText(properties.getAdmin().getPassword())) {
            return operator;
        }
        operator.setPasswordHash(passwordEncoder.encode(properties.getAdmin().getPassword()));
        return adminOperatorRepository.save(operator);
    }

    private String normalize(String username) {
        if (!StringUtils.hasText(username)) {
            return "admin";
        }
        return username.trim().toLowerCase();
    }
}
