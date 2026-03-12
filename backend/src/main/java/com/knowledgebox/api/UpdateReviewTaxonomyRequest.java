package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateReviewTaxonomyRequest(
        @NotBlank @Size(max = 128) String categoryName,
        List<@NotBlank @Size(max = 128) String> tags
) {
}
