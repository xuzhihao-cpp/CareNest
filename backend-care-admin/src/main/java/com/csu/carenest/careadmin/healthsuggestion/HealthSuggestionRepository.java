package com.csu.carenest.careadmin.healthsuggestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class HealthSuggestionRepository {
    private final JdbcTemplate jdbc;
    public HealthSuggestionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<OrderRow> order(String orderId) {
        return jdbc.query("SELECT o.order_id,o.elder_id,e.elder_name,si.service_name FROM nursing_order o JOIN elder_profile e ON e.elder_id=o.elder_id JOIN service_item si ON si.service_id=o.service_id WHERE o.order_id=?",
                (rs,n)->new OrderRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)), orderId).stream().findFirst();
    }
    public boolean assigned(String orderId,String nurseId) { return count("SELECT COUNT(*) FROM nurse_task WHERE order_id=? AND nurse_id=?",orderId,nurseId)>0; }
    public Optional<SourceRow> source(String orderId,String type,String id) {
        if ("SERVICE_RECORD".equals(type)) return jdbc.query("SELECT record_id,content,nursing_advice FROM care_service_record WHERE record_id=? AND order_id=?",
                (rs,n)->new SourceRow(rs.getString(1), join(rs.getString(2),rs.getString(3))),id,orderId).stream().findFirst();
        if ("SERVICE_REPORT".equals(type)) return jdbc.query("SELECT report_id,summary,nursing_advice FROM service_report WHERE report_id=? AND order_id=?",
                (rs,n)->new SourceRow(rs.getString(1), join(rs.getString(2),rs.getString(3))),id,orderId).stream().findFirst();
        return Optional.empty();
    }
    public List<Map<String,Object>> rows(String table,String elderId,String order) {
        return jdbc.queryForList("SELECT * FROM "+table+" WHERE elder_id=? "+order,elderId);
    }
    public boolean duplicate(String orderId,String sourceType,String sourceId,String field,String newValue) {
        return count("SELECT COUNT(*) FROM health_update_suggestion WHERE order_id=? AND source_type=? AND source_id=? AND field_name=? AND new_value=? AND suggestion_status='PENDING'",orderId,sourceType,sourceId,field,newValue)>0;
    }
    public void insertSuggestion(String id,OrderRow order,HealthSuggestionDtos.CreateRequest r,String oldValue,String newValue,String userId) {
        jdbc.update("INSERT INTO health_update_suggestion(suggestion_id,elder_id,order_id,field_name,old_value,new_value,source_type,source_id,reason,suggestion_status,created_by) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?)",
                id,order.elderId(),order.orderId(),r.fieldName(),oldValue,newValue,r.sourceType(),r.sourceId(),r.reason().trim(),userId);
    }
    public void insertTask(String taskId,String suggestionId,OrderRow order,HealthSuggestionDtos.CreateRequest r,String oldValue,String newValue,String userId) {
        jdbc.update("INSERT INTO health_info_review_task(review_task_id,suggestion_id,task_type,order_id,elder_id,field_name,old_value,new_value,source_type,source_id,review_status,created_by) VALUES(?,?,'HEALTH_UPDATE',?,?,?,?,?,?,?,'PENDING',?)",
                taskId,suggestionId,order.orderId(),order.elderId(),r.fieldName(),oldValue,newValue,r.sourceType(),r.sourceId(),userId);
        jdbc.update("UPDATE health_update_suggestion SET review_task_id=? WHERE suggestion_id=?",taskId,suggestionId);
    }
    public void log(String id,String userId,String suggestionId,String newValue) {
        jdbc.update("INSERT INTO operation_log(log_id,operator_id,role_code,operation_type,biz_type,biz_id,after_value,trace_id) VALUES(?,?,'NURSE','CREATE_HEALTH_UPDATE_SUGGESTION','HEALTH_UPDATE_SUGGESTION',?,?,?)",id,userId,suggestionId,newValue,id);
    }
    public boolean hasReviewPermission(String userId) {
        return count("SELECT COUNT(*) FROM user_role ur JOIN sys_role r ON r.role_id=ur.role_id JOIN role_permission rp ON rp.role_id=r.role_id JOIN sys_permission p ON p.permission_id=rp.permission_id WHERE ur.user_id=? AND r.enabled=1 AND p.enabled=1 AND p.permission_code IN ('HEALTH_REVIEW','HEALTH_ARCHIVE_REVIEW','health:review')",userId)>0;
    }
    public long reviewCount(String status,String source,String keyword) { Query q=query(status,source,keyword); return jdbc.queryForObject("SELECT COUNT(*) "+from()+q.where,Long.class,q.args.toArray()); }
    public List<ReviewRow> reviews(String status,String source,String keyword,int offset,int size) {
        Query q=query(status,source,keyword); q.args.add(size);q.args.add(offset);
        return jdbc.query("SELECT t.review_task_id,s.suggestion_id,t.review_status,e.elder_name,si.service_name,s.source_type,CASE s.source_type WHEN 'SERVICE_RECORD' THEN cr.content ELSE sr.summary END source_summary,s.field_name,s.old_value,s.new_value,s.reason,s.created_at "+from()+q.where+" ORDER BY s.created_at DESC LIMIT ? OFFSET ?",
                (rs,n)->new ReviewRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getTimestamp(12).toLocalDateTime()),q.args.toArray());
    }
    private String from(){return "FROM health_info_review_task t JOIN health_update_suggestion s ON s.suggestion_id=t.suggestion_id JOIN elder_profile e ON e.elder_id=s.elder_id JOIN nursing_order o ON o.order_id=s.order_id JOIN service_item si ON si.service_id=o.service_id LEFT JOIN care_service_record cr ON s.source_type='SERVICE_RECORD' AND cr.record_id=s.source_id LEFT JOIN service_report sr ON s.source_type='SERVICE_REPORT' AND sr.report_id=s.source_id ";}
    private Query query(String status,String source,String keyword){StringBuilder w=new StringBuilder("WHERE t.task_type='HEALTH_UPDATE'");List<Object>a=new ArrayList<>();if(text(status)){w.append(" AND t.review_status=?");a.add(status);}if(text(source)){w.append(" AND s.source_type=?");a.add(source);}if(text(keyword)){w.append(" AND (e.elder_name LIKE ? OR si.service_name LIKE ?)");a.add("%"+keyword.trim()+"%");a.add("%"+keyword.trim()+"%");}return new Query(w.toString(),a);}
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private static boolean text(String v){return v!=null&&!v.isBlank();} private static String join(String a,String b){return b==null||b.isBlank()?a:a+"；"+b;}
    private record Query(String where,List<Object>args){}
    public record OrderRow(String orderId,String elderId,String elderName,String serviceName){}
    public record SourceRow(String id,String summary){}
    public record ReviewRow(String taskId,String suggestionId,String status,String elderName,String serviceName,String sourceType,String sourceSummary,String fieldName,String oldValue,String newValue,String reason,LocalDateTime createdAt){}
}
