package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.AppToolCatalogItemView;
import com.knowledgebox.api.AppToolExecutionResultView;
import com.knowledgebox.api.ExecuteAppToolRequest;
import com.knowledgebox.security.CurrentUserAccessor;
import com.knowledgebox.service.apptool.AppToolCatalogService;
import com.knowledgebox.service.apptool.AppToolExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/tools")
public class UserToolController {

    private final AppToolCatalogService appToolCatalogService;
    private final AppToolExecutionService appToolExecutionService;
    private final CurrentUserAccessor currentUserAccessor;

    public UserToolController(
            AppToolCatalogService appToolCatalogService,
            AppToolExecutionService appToolExecutionService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.appToolCatalogService = appToolCatalogService;
        this.appToolExecutionService = appToolExecutionService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public List<AppToolCatalogItemView> tools() {
        return appToolCatalogService.catalog();
    }

    @PostMapping("/{code}/execute")
    public AppToolExecutionResultView execute(
            @PathVariable String code,
            @Valid @RequestBody ExecuteAppToolRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return appToolExecutionService.execute(
                currentUserAccessor.requireCurrentUser().id(),
                code,
                request.input(),
                resolveClientIp(httpServletRequest)
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String firstForwarded = forwarded.contains(",")
                ? forwarded.substring(0, forwarded.indexOf(','))
                : forwarded;
        return firstForwarded.trim();
    }
}
