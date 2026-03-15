package com.knowledgebox.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchReviewActionRequest(
        @NotEmpty List<@NotNull Long> reviewIds,
        @Size(max = 1000) String reason
) {
}
