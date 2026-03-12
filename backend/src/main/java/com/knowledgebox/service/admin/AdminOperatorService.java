package com.knowledgebox.service.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperatorService {

    private final AdminSecurityService adminSecurityService;

    public AdminOperatorService(AdminSecurityService adminSecurityService) {
        this.adminSecurityService = adminSecurityService;
    }

    @Transactional
    public Long resolveOperatorId(String username) {
        return adminSecurityService.ensureOperator(username).getId();
    }
}
