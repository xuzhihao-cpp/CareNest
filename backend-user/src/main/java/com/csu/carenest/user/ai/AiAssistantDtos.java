package com.csu.carenest.user.ai;

import java.util.List;

public final class AiAssistantDtos {
    private AiAssistantDtos() {}
    public record CreateSessionRequest(String elderId, String sessionTitle, String sourceType) {}
    public record MessageRequest(String content, String messageType, String voiceLogId) {}
    public record Session(String sessionId, String elderId, String elderName, String sessionTitle,
                          String sessionStatus, String safetyLevel, boolean riskFlag,
                          String latestAssistanceTicketId, String latestAssistanceStatus, String createdAt) {}
    public record MessageResult(String sessionId, String userMessageId, String assistantMessageId,
                                String answer, String safetyLevel, boolean riskFlag,
                                String assistanceTicketId, boolean customerServiceTicketCreated) {}
    public record SpeechTranscription(String transcript, String model, String traceId) {}
    public record AssistanceTicket(String ticketId, String elderId, String elderName, String category,
                                    String priority, String ticketStatus, String description,
                                    String sourceType, String createdAt) {}
    public record SessionSummary(String sessionId, String elderId, String elderName,
                                 String sessionTitle, String sessionStatus, String safetyLevel,
                                 String latestMessagePreview, String createdAt, String updatedAt) {}
    public record ConversationMessage(String messageId, String senderRole,
                                      String messageType, String content, boolean safetyFlag,
                                      String safetyLevel, String createdAt) {}
    public record PageResult<T>(List<T> records, long total, int page, int size) {}
}
