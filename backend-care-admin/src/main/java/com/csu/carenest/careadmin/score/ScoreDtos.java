package com.csu.carenest.careadmin.score;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 阶段47-48护理评分冻结响应及其可解释日志项。 */
public final class ScoreDtos {

    private ScoreDtos() {
    }

    public record RecalculateRequest(
            @NotBlank @Size(max = 32) String nurseId,
            @NotBlank @Size(max = 32) String sourceEventId) {
    }

    public record ScoreResponse(
            String nurseId,
            BigDecimal totalScore,
            String level,
            List<ScoreChangeItem> changeLogs) {
    }

    public record MyScoreResponse(
            BigDecimal totalScore,
            String level,
            BigDecimal monthDelta,
            List<ScoreChangeItem> items) {
    }

    public record ScoreChangeItem(
            String changeLogId,
            String sourceEventType,
            String sourceEventId,
            BigDecimal beforeScore,
            BigDecimal afterScore,
            BigDecimal scoreDelta,
            String reason,
            LocalDateTime createdAt,
            String targetType,
            String targetId) {
    }
}
