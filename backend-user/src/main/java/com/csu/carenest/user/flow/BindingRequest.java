package com.csu.carenest.user.flow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BindingRequest(
        @NotBlank String elderInviteCode,
        @NotBlank String relationType,
        @NotEmpty List<String> scopeCodes
) {
}
