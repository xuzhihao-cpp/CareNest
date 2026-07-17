package com.csu.carenest.careadmin.training;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 阶段49-50文章、规则与阅读记录数据访问。 */
@Repository
public class Phase49To50TrainingRepository {

    private final JdbcTemplate jdbcTemplate;

    public Phase49To50TrainingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(String userId, String permission) {
        return count("""
                SELECT COUNT(*) FROM user_role ur
                JOIN sys_role r ON r.role_id=ur.role_id AND r.enabled=1
                JOIN role_permission rp ON rp.role_id=r.role_id
                JOIN sys_permission p ON p.permission_id=rp.permission_id AND p.enabled=1
                WHERE ur.user_id=? AND p.permission_code=?
                """, userId, permission) > 0;
    }

    public boolean serviceExists(String serviceId) {
        return count("SELECT COUNT(*) FROM service_item WHERE service_id=?", serviceId) > 0;
    }

    public void insertArticle(
            String articleId, TrainingDtos.ArticleRequest request,
            String firstServiceId, String userId) {
        jdbcTemplate.update("""
                INSERT INTO training_article
                  (article_id,title,content_summary,content_url,service_id,
                   required_reading,article_status,created_by)
                VALUES (?,?,?,?,?,?,'DRAFT',?)
                """, articleId, request.title().trim(), trim(request.summary()),
                trim(request.contentUrl()), firstServiceId, request.requiredRead(), userId);
    }

    public Optional<ArticleContext> findArticle(String articleId) {
        List<ArticleContext> rows = jdbcTemplate.query("""
                SELECT article_id,article_status,required_reading FROM training_article WHERE article_id=?
                """, (rs, rowNum) -> new ArticleContext(
                rs.getString("article_id"), rs.getString("article_status"),
                rs.getBoolean("required_reading")), articleId);
        return rows.stream().findFirst();
    }

    public List<TrainingDtos.ArticleResponse> findArticles() {
        List<String> articleIds = jdbcTemplate.query("""
                SELECT article_id FROM training_article
                ORDER BY created_at,article_id
                """, (rs, rowNum) -> rs.getString("article_id"));
        return articleIds.stream().map(this::findArticleResponse)
                .flatMap(Optional::stream).toList();
    }

    public Optional<TrainingDtos.ArticleResponse> findArticleResponse(String articleId) {
        List<ArticleDetails> rows = jdbcTemplate.query("""
                SELECT article_id,title,content_summary,content_url,required_reading,article_status
                FROM training_article WHERE article_id=?
                """, (rs, rowNum) -> new ArticleDetails(
                rs.getString("article_id"), rs.getString("title"),
                rs.getString("content_summary"), rs.getString("content_url"),
                rs.getBoolean("required_reading"), rs.getString("article_status")), articleId);
        return rows.stream().findFirst().map(details -> new TrainingDtos.ArticleResponse(
                details.articleId(), details.title(), value(details.summary()), value(details.contentUrl()),
                findTags(articleId, false), findServiceIds(articleId), findTags(articleId, true),
                details.requiredRead(), details.status()));
    }

    public void updateArticle(
            String articleId, TrainingDtos.ArticleRequest request, String firstServiceId) {
        jdbcTemplate.update("""
                UPDATE training_article SET title=?,content_summary=?,content_url=?,service_id=?,
                    required_reading=?,article_status=? WHERE article_id=?
                """, request.title().trim(), trim(request.summary()), trim(request.contentUrl()),
                firstServiceId, request.requiredRead(), request.status(), articleId);
    }

    public void updateArticleContent(
            String articleId, TrainingDtos.ArticleRequest request, String firstServiceId) {
        jdbcTemplate.update("""
                UPDATE training_article SET title=?,content_summary=?,content_url=?,service_id=?,
                    required_reading=? WHERE article_id=?
                """, request.title().trim(), trim(request.summary()), trim(request.contentUrl()),
                firstServiceId, request.requiredRead(), articleId);
    }

    public int updateArticleStatus(String articleId, String status) {
        return jdbcTemplate.update("""
                UPDATE training_article SET article_status=?,
                  published_at=CASE WHEN ?='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END,
                  offline_at=CASE WHEN ?='OFFLINE' THEN CURRENT_TIMESTAMP ELSE offline_at END
                WHERE article_id=? AND article_status<>?
                """, status, status, status, articleId, status);
    }

    public void replaceTagsAndRules(
            String articleId, List<String> tags, List<String> riskTags,
            List<String> serviceIds, java.util.function.Function<String, String> idFactory) {
        jdbcTemplate.update("DELETE FROM article_tag WHERE article_id=?", articleId);
        jdbcTemplate.update("DELETE FROM article_recommend_rule WHERE article_id=?", articleId);
        int sort = 0;
        for (String tag : tags) {
            jdbcTemplate.update("""
                    INSERT INTO article_tag(tag_id,article_id,tag_code,tag_name) VALUES (?,?,?,?)
                    """, idFactory.apply("tag"), articleId, "TAG:" + tag, tag);
        }
        for (String riskTag : riskTags) {
            jdbcTemplate.update("""
                    INSERT INTO article_tag(tag_id,article_id,tag_code,tag_name) VALUES (?,?,?,?)
                    """, idFactory.apply("tag"), articleId, "RISK:" + riskTag, riskTag);
        }
        if (serviceIds.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO article_recommend_rule(rule_id,article_id,service_id,enabled,sort)
                    VALUES (?,?,NULL,1,0)
                    """, idFactory.apply("article_rule"), articleId);
        } else {
            for (String serviceId : serviceIds) {
                jdbcTemplate.update("""
                        INSERT INTO article_recommend_rule(rule_id,article_id,service_id,enabled,sort)
                        VALUES (?,?,?,1,?)
                        """, idFactory.apply("article_rule"), articleId, serviceId, ++sort);
            }
        }
    }

    public Optional<OrderContext> findOrder(String orderId) {
        List<OrderContext> rows = jdbcTemplate.query("""
                SELECT o.order_id,o.elder_id,o.service_id,o.order_status,nt.nurse_id
                FROM nursing_order o JOIN nurse_task nt ON nt.order_id=o.order_id
                WHERE o.order_id=?
                """, (rs, rowNum) -> new OrderContext(
                rs.getString("order_id"), rs.getString("elder_id"),
                rs.getString("service_id"), rs.getString("order_status"),
                rs.getString("nurse_id")), orderId);
        return rows.stream().findFirst();
    }

    public List<TrainingDtos.ReadResponse> findRecommended(String orderId, String nurseId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT a.article_id,a.title,a.content_summary,a.content_url,
                       a.required_reading,COALESCE(r.reading_status,'UNREAD') AS reading_status
                FROM nursing_order o
                JOIN training_article a ON a.article_status='PUBLISHED'
                JOIN article_recommend_rule ar ON ar.article_id=a.article_id AND ar.enabled=1
                  AND (ar.service_id IS NULL OR ar.service_id=o.service_id)
                LEFT JOIN nurse_article_reading r ON r.article_id=a.article_id AND r.nurse_id=?
                WHERE o.order_id=?
                  AND (NOT EXISTS (
                        SELECT 1 FROM article_tag at
                        WHERE at.article_id=a.article_id AND
                          (at.tag_code LIKE 'RISK:%' OR EXISTS (
                            SELECT 1 FROM risk_tag rt0
                            WHERE rt0.tag_code=at.tag_code OR rt0.tag_code=at.tag_name)))
                       OR EXISTS (
                        SELECT 1 FROM article_tag at JOIN risk_tag rt
                          ON at.tag_code=CONCAT('RISK:',rt.tag_code)
                             OR at.tag_name=rt.tag_code OR at.tag_code=rt.tag_code
                        WHERE at.article_id=a.article_id AND rt.elder_id=o.elder_id))
                ORDER BY a.article_id
                """, (rs, rowNum) -> new TrainingDtos.ReadResponse(
                rs.getString("article_id"), rs.getString("title"),
                value(rs.getString("content_summary")), value(rs.getString("content_url")),
                rs.getBoolean("required_reading"), rs.getString("reading_status")), nurseId, orderId);
    }

    public boolean isRecommended(String orderId, String articleId, String nurseId) {
        return findRecommended(orderId, nurseId).stream()
                .anyMatch(item -> item.articleId().equals(articleId));
    }

    public void markRead(String readingId, String articleId, String nurseId) {
        int updated = jdbcTemplate.update("""
                UPDATE nurse_article_reading SET reading_status='READ',read_at=CURRENT_TIMESTAMP
                WHERE article_id=? AND nurse_id=?
                """, articleId, nurseId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO nurse_article_reading
                      (reading_id,article_id,nurse_id,reading_status,read_at)
                    VALUES (?,?,?,'READ',CURRENT_TIMESTAMP)
                    """, readingId, articleId, nurseId);
        }
    }

    public void insertOperationLog(
            String logId, String operatorId, String roleCode, String operationType,
            String bizType, String bizId, String beforeJson, String afterJson, String traceId) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id,operator_id,role_code,operation_type,biz_type,biz_id,
                   before_value,after_value,trace_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, logId, operatorId, roleCode, operationType, bizType, bizId,
                beforeJson, afterJson, traceId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private List<String> findTags(String articleId, boolean risk) {
        String riskCondition = """
                (at.tag_code LIKE 'RISK:%' OR EXISTS (
                  SELECT 1 FROM risk_tag rt
                  WHERE rt.tag_code=at.tag_code OR rt.tag_code=at.tag_name))
                """;
        return jdbcTemplate.query("""
                SELECT at.tag_name FROM article_tag at
                WHERE at.article_id=? AND """ + (risk ? riskCondition : " NOT " + riskCondition) + """
                ORDER BY at.tag_id
                """, (rs, rowNum) -> rs.getString("tag_name"), articleId);
    }

    private List<String> findServiceIds(String articleId) {
        return jdbcTemplate.query("""
                SELECT service_id FROM article_recommend_rule
                WHERE article_id=? AND enabled=1 AND service_id IS NOT NULL
                ORDER BY sort,rule_id
                """, (rs, rowNum) -> rs.getString("service_id"), articleId);
    }

    public record ArticleContext(String articleId, String status, boolean requiredReading) {
    }

    private record ArticleDetails(
            String articleId, String title, String summary, String contentUrl,
            boolean requiredRead, String status) {
    }

    public record OrderContext(
            String orderId, String elderId, String serviceId,
            String status, String nurseId) {
    }
}
