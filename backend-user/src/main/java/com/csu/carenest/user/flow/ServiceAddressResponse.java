package com.csu.carenest.user.flow;

public record ServiceAddressResponse(
        String addressId,
        String fullAddress,
        Boolean isDefault,
        String contactName,
        String contactPhone,
        String regionCode,
        String detailAddress
) {
}
