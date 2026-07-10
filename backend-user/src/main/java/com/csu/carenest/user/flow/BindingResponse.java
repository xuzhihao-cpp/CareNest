package com.csu.carenest.user.flow;

import java.util.List;

public record BindingResponse(
        String bindingId,
        String elderId,
        String elderName,
        String relationType,
        String bindingStatus,
        List<String> scopeCodes
) {
}
