package com.knowledgebox.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCodeLoginRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 4, max = 8) String verificationCode
) {
}
