package com.csu.carenest.careadmin.aiaudit;

import java.time.LocalDateTime;
import java.util.List;

public final class AiAuditDtos {
    private AiAuditDtos() {
    }

    public record SessionItem(
            String sessionId,
            String elderName,
            String requesterName,
            String sessionTitle,
            String sessionStatus,
            String safetyLevel,
            boolean riskFlag,
            String customerServiceTicketId,
            String customerServiceTicketStatus,
            boolean pendingHumanReply,
            String latestMessageSummary,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record MessageItem(
            String senderRole,
            String messageType,
            String contentSummary,
            String content,
            boolean safetyFlag,
            LocalDateTime createdAt) {
    }

    public record SessionDetail(SessionItem session, List<MessageItem> messages) {
    }

    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }
}
