package com.csu.carenest.user.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class AiAssistantRepository {
    private final JdbcTemplate jdbc;
    public AiAssistantRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Elder> elderByUser(String userId) { return elder("user_id", userId); }
    public Optional<Elder> elder(String id) { return elder("elder_id", id); }
    private Optional<Elder> elder(String col, String val) { return jdbc.query("SELECT elder_id,elder_name,user_id FROM elder_profile WHERE " + col + "=?", (r,n)->new Elder(r.getString(1),r.getString(2),r.getString(3)), val).stream().findFirst(); }
    public boolean bound(String familyId, String elderId) { return jdbc.queryForObject("SELECT COUNT(*) FROM elder_family_binding WHERE family_id=? AND elder_id=? AND binding_status='ACTIVE'", Integer.class, familyId, elderId) > 0; }
    public String sessionOwner(String sessionId) { return jdbc.queryForObject("SELECT user_id FROM ai_assistant_session WHERE session_id=?", String.class, sessionId); }
    public Optional<String> sessionElder(String sessionId) { return jdbc.query("SELECT elder_id FROM ai_assistant_session WHERE session_id=?", (r,n)->r.getString(1), sessionId).stream().findFirst(); }
    public void createSession(String id, String elderId, String userId, String title, String source, String trace) { jdbc.update("INSERT INTO ai_assistant_session(session_id,elder_id,user_id,session_title,source_type,trace_id) VALUES (?,?,?,?,?,?)", id,elderId,userId,title,source,trace); }
    public void addMessage(String id,String session,String role,String type,String summary,String content,String voice,boolean safety,String trace) { jdbc.update("INSERT INTO ai_assistant_message(message_id,session_id,sender_role,message_type,content_summary,content_text,voice_log_id,safety_flag,trace_id) VALUES (?,?,?,?,?,?,?,?,?)",id,session,role,type,summary,content,voice,safety,trace); }
    public void updateSafety(String session,String level,boolean risk) { jdbc.update("UPDATE ai_assistant_session SET safety_level=?,risk_flag=?,updated_at=CURRENT_TIMESTAMP WHERE session_id=?",level,risk,session); }
    public void close(String session) { jdbc.update("UPDATE ai_assistant_session SET session_status='CLOSED',updated_at=CURRENT_TIMESTAMP WHERE session_id=?",session); }
    public String assistance(String id,String elder,String requester,String session,String category,String priority,String description) { jdbc.update("INSERT INTO assistance_ticket(assistance_ticket_id,elder_id,requester_id,session_id,category,priority,ticket_status,description,source_type) VALUES (?,?,?,?,?,?,?,?, 'AI')",id,elder,requester,session,category,priority,"PENDING",description); return id; }
    public void customerTicket(String id,String assistance,String elder,String requester,String category,String priority,String description) { jdbc.update("INSERT INTO customer_service_ticket(ticket_id,assistance_ticket_id,elder_id,requester_id,category,priority,ticket_status,description,source_type,source_id) VALUES (?,?,?,?,?,?, 'PENDING',?,?,?)",id,assistance,elder,requester,category,priority,description,"AI",assistance); }
    public List<AiAssistantDtos.AssistanceTicket> tickets(String elderId,String status,int limit,int offset) { String where=status==null?"":" AND t.ticket_status=?"; Object[] args=status==null?new Object[]{elderId,limit,offset}:new Object[]{elderId,status,limit,offset}; return jdbc.query("SELECT t.assistance_ticket_id,t.elder_id,e.elder_name,t.category,t.priority,t.ticket_status,t.description,t.source_type,t.created_at FROM assistance_ticket t JOIN elder_profile e ON e.elder_id=t.elder_id WHERE t.elder_id=?"+where+" ORDER BY t.created_at DESC LIMIT ? OFFSET ?",(r,n)->new AiAssistantDtos.AssistanceTicket(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9)),args); }
    public long ticketCount(String elderId,String status) { return status==null?jdbc.queryForObject("SELECT COUNT(*) FROM assistance_ticket WHERE elder_id=?",Long.class,elderId):jdbc.queryForObject("SELECT COUNT(*) FROM assistance_ticket WHERE elder_id=? AND ticket_status=?",Long.class,elderId,status); }
    public record Elder(String id,String name,String userId) {}
}
