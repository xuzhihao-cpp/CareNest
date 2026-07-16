package com.csu.carenest.user.reminder;

import java.time.LocalDateTime;
import java.util.List;

public final class ReminderDtos {
    private ReminderDtos() {}

    public record Item(String reminderId, String title, String content, LocalDateTime reminderAt,
                       String status, LocalDateTime snoozedUntil, LocalDateTime completedAt,
                       LocalDateTime needsHelpAt, String sourceType) {}

    public record ActionRequest(String action, Integer snoozeMinutes, String note) {}

    public record ActionResult(Item reminder, RecordItem record) {}

    public record RecordItem(String reminderId, String title, String action, String fromStatus,
                             String toStatus, String note, LocalDateTime actedAt) {}

    public record PageResult<T>(List<T> records, long total, int page, int size) {}
}
