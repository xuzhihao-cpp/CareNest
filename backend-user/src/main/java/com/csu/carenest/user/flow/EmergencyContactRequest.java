package com.csu.carenest.user.flow;

import jakarta.validation.constraints.NotBlank;

public record EmergencyContactRequest(
        @NotBlank String contactName,
        @NotBlank String contactPhone,
        String relationType
) {
}
