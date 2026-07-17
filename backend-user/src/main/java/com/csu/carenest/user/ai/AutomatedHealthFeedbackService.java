package com.csu.carenest.user.ai;

import com.csu.carenest.user.redis.HomeCacheInvalidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AutomatedHealthFeedbackService {
    private static final List<String> TYPES = List.of("PAIN", "DIZZINESS", "SLEEP", "DIET", "MENTAL_STATE");
    private static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH");
    private final JdbcTemplate jdbc;
    private final HomeCacheInvalidator cache;

    public AutomatedHealthFeedbackService(JdbcTemplate jdbc, HomeCacheInvalidator cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    @Transactional
    public String submit(String elderId, String userId, String content, AiProvider.Result result) {
        if (result == null || !result.feedbackRequested()
                || !TYPES.contains(result.feedbackType()) || !SEVERITIES.contains(result.feedbackSeverity())) return null;
        String original = content == null ? "" : content.trim();
        if (original.isEmpty() || original.length() > 512) return null;
        List<String> existing = jdbc.queryForList("""
                SELECT feedback_id FROM elder_health_feedback
                WHERE elder_id=? AND created_by=? AND feedback_type=? AND severity=? AND content=?
                  AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE)
                ORDER BY created_at DESC LIMIT 1
                """, String.class, elderId, userId, result.feedbackType(), result.feedbackSeverity(), original);
        if (!existing.isEmpty()) return existing.get(0);
        String feedbackId = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO elder_health_feedback
                  (feedback_id,elder_id,feedback_type,severity,content,input_type,file_id,created_by)
                VALUES (?,?,?,?,?,'TEXT',NULL,?)
                """, feedbackId, elderId, result.feedbackType(), result.feedbackSeverity(), original, userId);
        jdbc.queryForList("SELECT DISTINCT family_id FROM elder_family_binding WHERE elder_id=? AND binding_status='ACTIVE'", String.class, elderId)
                .forEach(familyId -> cache.evictAfterCommit("FAMILY", familyId));
        cache.evictAfterCommit("ELDER", userId);
        return feedbackId;
    }
}
