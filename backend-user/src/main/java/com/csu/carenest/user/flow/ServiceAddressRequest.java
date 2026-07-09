package com.csu.carenest.user.flow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceAddressRequest(
        @NotBlank String contactName,
        @NotBlank String contactPhone,
        @NotBlank String regionCode,
        @NotBlank String detailAddress,
        @NotNull Boolean isDefault
) {
}
