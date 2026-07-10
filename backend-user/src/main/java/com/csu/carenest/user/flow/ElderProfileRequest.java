package com.csu.carenest.user.flow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ElderProfileRequest(
        @NotBlank String name,
        @NotBlank String gender,
        @NotBlank String birthDate,
        @NotBlank String careLevel,
        @Valid @NotEmpty List<EmergencyContactRequest> emergencyContacts
) {
}
