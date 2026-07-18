package com.csu.carenest.careadmin.reminder;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class ReminderDtos {

    private ReminderDtos() {
    }

    public record ReminderItem(
            String reminderId,
            String elderId,
            String elderName,
            String reminderType,
            String title,
            String content,
            LocalDateTime scheduledAt,
            String reminderStatus,
            String sourceType,
            String sourceId,
            String createdBy,
            String createdByName) {
    }

    public record ReminderUpsertRequest(
            @NotBlank String reminderType,
            @NotBlank String title,
            String content,
            @NotBlank String scheduledAt,
            String reminderStatus) {
    }

    public record ReminderDeleteResponse(String reminderId) {
    }

    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }
}
