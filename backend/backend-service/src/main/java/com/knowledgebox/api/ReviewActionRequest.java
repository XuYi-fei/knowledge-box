package com.knowledgebox.api;

import jakarta.validation.constraints.Size;

public record ReviewActionRequest(
        @Size(max = 1000) String reason
) {
}
