package com.csu.carenest.careadmin.aiaudit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AiAuditRepository {
    private final JdbcTemplate jdbc;

    public AiAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasPermission(String userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role ur
                JOIN role_permission rp ON rp.role_id=ur.role_id
                JOIN sys_permission p ON p.permission_id=rp.permission_id AND p.enabled=1
                WHERE ur.user_id=? AND p.permission_code='AI_SESSION_REVIEW'
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public List<AiAuditDtos.SessionItem> list(
            Boolean riskFlag, String safetyLevel, int limit, int offset) {
        Query query = query(riskFlag, safetyLevel);
        query.args().add(limit);
        query.args().add(offset);
        return jdbc.query("""
                SELECT s.session_id,e.elder_name,u.display_name,s.session_title,
                       s.session_status,s.safety_level,s.risk_flag,cst.ticket_id,cst.ticket_status,
                       CASE WHEN s.safety_level='CRITICAL' AND cst.ticket_id IS NOT NULL
                             AND NOT EXISTS (SELECT 1 FROM ticket_message tm WHERE tm.ticket_id=cst.ticket_id AND tm.sender_role IN ('CUSTOMER_SERVICE','ADMIN') AND tm.message_type='TEXT')
                            THEN 1 ELSE 0 END pending_human_reply,
                       (SELECT m.content_summary FROM ai_assistant_message m
                        WHERE m.session_id=s.session_id
                        ORDER BY m.created_at DESC,m.message_id DESC LIMIT 1) latest_message,
                       s.created_at,s.updated_at
                FROM ai_assistant_session s
                JOIN elder_profile e ON e.elder_id=s.elder_id
                JOIN sys_user u ON u.user_id=s.user_id
                LEFT JOIN customer_service_ticket cst ON cst.ticket_id=(SELECT ct.ticket_id FROM assistance_ticket at JOIN customer_service_ticket ct ON ct.assistance_ticket_id=at.assistance_ticket_id WHERE at.session_id=s.session_id ORDER BY ct.created_at DESC LIMIT 1)
                """ + query.where() + " ORDER BY pending_human_reply DESC,s.risk_flag DESC,s.updated_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapSession(rs), query.args().toArray());
    }

    public long count(Boolean riskFlag, String safetyLevel) {
        Query query = query(riskFlag, safetyLevel);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_assistant_session s " + query.where(),
                Long.class, query.args().toArray());
        return count == null ? 0 : count;
    }

    public Optional<AiAuditDtos.SessionItem> find(String sessionId) {
        return jdbc.query("""
                SELECT s.session_id,e.elder_name,u.display_name,s.session_title,
                       s.session_status,s.safety_level,s.risk_flag,cst.ticket_id,cst.ticket_status,
                       CASE WHEN s.safety_level='CRITICAL' AND cst.ticket_id IS NOT NULL
                             AND NOT EXISTS (SELECT 1 FROM ticket_message tm WHERE tm.ticket_id=cst.ticket_id AND tm.sender_role IN ('CUSTOMER_SERVICE','ADMIN') AND tm.message_type='TEXT')
                            THEN 1 ELSE 0 END pending_human_reply,
                       (SELECT m.content_summary FROM ai_assistant_message m
                        WHERE m.session_id=s.session_id
                        ORDER BY m.created_at DESC,m.message_id DESC LIMIT 1) latest_message,
                       s.created_at,s.updated_at
                FROM ai_assistant_session s
                JOIN elder_profile e ON e.elder_id=s.elder_id
                JOIN sys_user u ON u.user_id=s.user_id
                LEFT JOIN customer_service_ticket cst ON cst.ticket_id=(SELECT ct.ticket_id FROM assistance_ticket at JOIN customer_service_ticket ct ON ct.assistance_ticket_id=at.assistance_ticket_id WHERE at.session_id=s.session_id ORDER BY ct.created_at DESC LIMIT 1)
                WHERE s.session_id=?
                """, (rs, rowNum) -> mapSession(rs), sessionId).stream().findFirst();
    }

    public List<AiAuditDtos.MessageItem> messages(String sessionId) {
        return jdbc.query("""
                SELECT sender_role,message_type,content_summary,content_text,safety_flag,created_at
                FROM ai_assistant_message WHERE session_id=?
                ORDER BY created_at,message_id
                """, (rs, rowNum) -> new AiAuditDtos.MessageItem(
                rs.getString("sender_role"), rs.getString("message_type"),
                rs.getString("content_summary"), rs.getString("content_text"),
                rs.getBoolean("safety_flag"), rs.getTimestamp("created_at").toLocalDateTime()), sessionId);
    }

    private AiAuditDtos.SessionItem mapSession(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AiAuditDtos.SessionItem(
                rs.getString("session_id"), rs.getString("elder_name"),
                rs.getString("display_name"), rs.getString("session_title"),
                rs.getString("session_status"), rs.getString("safety_level"),
                rs.getBoolean("risk_flag"), rs.getString("ticket_id"), rs.getString("ticket_status"),
                rs.getBoolean("pending_human_reply"), rs.getString("latest_message"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private Query query(Boolean riskFlag, String safetyLevel) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (riskFlag != null) {
            where.append(" AND s.risk_flag=?");
            args.add(riskFlag);
        }
        if (safetyLevel != null && !safetyLevel.isBlank()) {
            where.append(" AND s.safety_level=?");
            args.add(safetyLevel.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return new Query(where.toString(), args);
    }

    private record Query(String where, List<Object> args) {
    }
}
