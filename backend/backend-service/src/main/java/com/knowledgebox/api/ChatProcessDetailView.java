package com.knowledgebox.api;

public record ChatProcessDetailView(
        String kind,
        String summary,
        String detail,
        String statusLabel,
        String statusTone
) {
}
