package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;

public record FamilyOrderRequest(
        @NotBlank String elderId,
        @NotBlank String serviceId,
        @NotBlank String addressId,
        @NotBlank String scheduledStart,
        String preferredNurseId,
        String remark
) {
}
