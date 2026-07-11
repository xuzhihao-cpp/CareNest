package com.csu.carenest.user.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReportAckRequest(
        @NotBlank String ackResult,
        @NotNull @Min(0) @Max(100) Integer satisfaction,
        @NotBlank String remark,
        @NotNull List<String> acceptedSuggestionIds
) {
}
