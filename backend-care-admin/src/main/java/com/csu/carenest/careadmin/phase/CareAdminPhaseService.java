package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.csu.carenest.careadmin.common.PageData;
import com.csu.carenest.careadmin.phase.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 成员3阶段8-18核心业务服务。
 * 仅负责护理端与管理端后端：服务项目、订单查询、派单、护理任务、护理执行、服务报告、取消改期、演示数据状态。
 */
@Service
public class CareAdminPhaseService {

    private static final String WAIT_DISPATCH = "WAIT_DISPATCH";
    private static final String DISPATCHED = "DISPATCHED";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String ON_THE_WAY = "ON_THE_WAY";
    private static final String SERVING = "SERVING";
    private static final String WAIT_REPORT = "WAIT_REPORT";
    private static final String WAIT_CONFIRM = "WAIT_CONFIRM";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELED = "CANCELED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CareAdminPhaseService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ServiceItemResponse> serviceItems() {
        return jdbcTemplate.query("""
                SELECT service_id, service_name, price_cent, duration_minutes, service_status
                FROM service_item
                ORDER BY sort, service_id
                """, (rs, rowNum) -> new ServiceItemResponse(
                rs.getString("service_id"),
                rs.getString("service_name"),
                rs.getInt("price_cent"),
                rs.getInt("duration_minutes"),
                rs.getString("service_status")
        ));
    }

    public ServiceItemResponse serviceItem(String serviceId) {
        return queryOne("""
                SELECT service_id, service_name, price_cent, duration_minutes, service_status
                FROM service_item
                WHERE service_id = ?
                """, row -> new ServiceItemResponse(
                string(row, "service_id"),
                string(row, "service_name"),
                integer(row, "price_cent"),
                integer(row, "duration_minutes"),
                string(row, "service_status")
        ), serviceId);
    }

    @Transactional
    public ServiceItemResponse createServiceItem(CurrentUser currentUser, ServiceItemRequest request) {
        String serviceId = nextId("service");
        jdbcTemplate.update("""
                INSERT INTO service_item
                  (service_id, service_name, service_desc, price_cent, duration_minutes, service_status, sort)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, serviceId, request.serviceName(), request.category(), request.price(),
                request.durationMinutes(), normalizeServiceStatus(request.status()), 99);
        saveOperationLog(currentUser, "CREATE_SERVICE_ITEM", "SERVICE_ITEM", serviceId, null, request);
        return serviceItem(serviceId);
    }

    @Transactional
    public ServiceItemResponse updateServiceItem(CurrentUser currentUser, String serviceId, ServiceItemRequest request) {
        Map<String, Object> before = requireRow("SELECT * FROM service_item WHERE service_id = ?", serviceId);
        jdbcTemplate.update("""
                UPDATE service_item
                SET service_name = ?, service_desc = ?, price_cent = ?, duration_minutes = ?, service_status = ?
                WHERE service_id = ?
                """, request.serviceName(), request.category(), request.price(),
                request.durationMinutes(), normalizeServiceStatus(request.status()), serviceId);
        saveOperationLog(currentUser, "UPDATE_SERVICE_ITEM", "SERVICE_ITEM", serviceId, before, request);
        return serviceItem(serviceId);
    }

    public OrderDetailResponse orderDetail(CurrentUser currentUser, String orderId) {
        OrderDetailResponse detail = mapOrderDetail(requireRow(orderDetailSql() + " WHERE o.order_id = ?", orderId));
        if (currentUser.hasRole(RoleCode.ADMIN) || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE)) {
            return detail;
        }
        if (currentUser.hasRole(RoleCode.FAMILY) && currentUser.userId().equals(detail.familyId())) {
            return detail;
        }
        if (currentUser.hasRole(RoleCode.NURSE) && taskBelongsToNurse(orderId, currentUser.userId())) {
            return detail;
        }
        throw new ForbiddenException();
    }

    public PageData<OrderDetailResponse> adminOrders(
            String orderStatus,
            String keyword,
            String dateFrom,
            String dateTo,
            int page,
            int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (hasText(orderStatus)) {
            where.append(" AND o.order_status = ?");
            args.add(orderStatus);
        }
        if (hasText(keyword)) {
            where.append(" AND (o.order_id LIKE ? OR e.elder_name LIKE ? OR si.service_name LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (hasText(dateFrom)) {
            where.append(" AND o.scheduled_start_at >= ?");
            args.add(parseDateTime(dateFrom));
        }
        if (hasText(dateTo)) {
            where.append(" AND o.scheduled_start_at <= ?");
            args.add(parseDateTime(dateTo));
        }
        return orderPage(where.toString(), args, page, size);
    }

    @Transactional
    public DispatchResponse dispatchOrder(CurrentUser currentUser, String orderId, DispatchRequest request) {
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        requireStatus(order, WAIT_DISPATCH);
        requireRoleUser(request.nurseId(), "NURSE");
        String taskId = nextId("task");
        jdbcTemplate.update("""
                INSERT INTO nurse_task (task_id, order_id, nurse_id, task_status, dispatch_remark)
                VALUES (?, ?, ?, ?, ?)
                """, taskId, orderId, request.nurseId(), DISPATCHED, request.dispatchRemark());
        changeOrderStatus(currentUser.userId(), order, DISPATCHED, "DISPATCH_ORDER");
        saveOperationLog(currentUser, "DISPATCH_ORDER", "NURSE_TASK", taskId, null, request);
        return new DispatchResponse(orderId, DISPATCHED, taskId);
    }

    @Transactional
    public DispatchResponse acceptTask(CurrentUser currentUser, String taskId) {
        Map<String, Object> task = requireRow("SELECT * FROM nurse_task WHERE task_id = ?", taskId);
        requireNurseTaskAccess(currentUser, task);
        requireTaskStatus(task, DISPATCHED);
        jdbcTemplate.update("UPDATE nurse_task SET task_status = ?, accepted_at = CURRENT_TIMESTAMP WHERE task_id = ?",
                ACCEPTED, taskId);
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", string(task, "order_id"));
        changeOrderStatus(currentUser.userId(), order, ACCEPTED, "ACCEPT_TASK");
        return new DispatchResponse(string(task, "order_id"), ACCEPTED, taskId);
    }

    @Transactional
    public DispatchResponse updateTaskStatus(CurrentUser currentUser, String taskId, TaskStatusRequest request) {
        Map<String, Object> task = requireRow("SELECT * FROM nurse_task WHERE task_id = ?", taskId);
        requireNurseTaskAccess(currentUser, task);
        String targetStatus = hasText(request.targetStatus()) ? request.targetStatus() : ON_THE_WAY;
        if (!List.of(ON_THE_WAY, SERVING, COMPLETED, CANCELED).contains(targetStatus)) {
            throw new BusinessRuleException();
        }

        String timeColumn = switch (targetStatus) {
            case SERVING -> ", started_at = CURRENT_TIMESTAMP";
            case COMPLETED -> ", completed_at = CURRENT_TIMESTAMP";
            default -> "";
        };
        jdbcTemplate.update("UPDATE nurse_task SET task_status = ?" + timeColumn + " WHERE task_id = ?",
                targetStatus, taskId);

        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", string(task, "order_id"));
        String orderTargetStatus = COMPLETED.equals(targetStatus) ? WAIT_REPORT : targetStatus;
        changeOrderStatus(currentUser.userId(), order, orderTargetStatus, "UPDATE_TASK_STATUS");
        return new DispatchResponse(string(task, "order_id"), orderTargetStatus, taskId);
    }

    public PageData<TaskResponse> nurseTasks(CurrentUser currentUser, String status, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (currentUser.hasRole(RoleCode.NURSE) && !currentUser.hasRole(RoleCode.ADMIN)) {
            where.append(" AND nt.nurse_id = ?");
            args.add(currentUser.userId());
        }
        if (hasText(status)) {
            where.append(" AND nt.task_status = ?");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM nurse_task nt " + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(offset(page, size));
        List<TaskResponse> records = jdbcTemplate.query(taskSql() + " " + where
                        + " ORDER BY o.scheduled_start_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new TaskResponse(
                        rs.getString("task_id"),
                        rs.getString("order_id"),
                        rs.getString("nurse_id"),
                        rs.getString("task_status"),
                        rs.getString("order_status"),
                        rs.getString("dispatch_remark"),
                        toText(rs.getObject("scheduled_start_at"))
                ), queryArgs.toArray());
        return new PageData<>(records, total == null ? 0 : total, page, size);
    }

    public TaskResponse taskDetail(CurrentUser currentUser, String taskId) {
        Map<String, Object> task = requireRow(taskSql() + " WHERE nt.task_id = ?", taskId);
        if (currentUser.hasRole(RoleCode.NURSE) && !currentUser.userId().equals(string(task, "nurse_id"))
                && !currentUser.hasRole(RoleCode.ADMIN)) {
            throw new ForbiddenException();
        }
        return new TaskResponse(
                string(task, "task_id"),
                string(task, "order_id"),
                string(task, "nurse_id"),
                string(task, "task_status"),
                string(task, "order_status"),
                string(task, "dispatch_remark"),
                toText(task.get("scheduled_start_at"))
        );
    }

    @Transactional
    public ServiceRecordResponse createServiceRecord(CurrentUser currentUser, String orderId, ServiceRecordRequest request) {
        Map<String, Object> task = requireTaskByOrderAndUser(orderId, currentUser);
        String recordId = nextId("record");
        jdbcTemplate.update("""
                INSERT INTO care_service_record
                  (record_id, order_id, task_id, nurse_id, start_time, end_time, content,
                   nursing_advice, abnormal_flag, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, recordId, orderId, string(task, "task_id"), string(task, "nurse_id"),
                parseDateTime(request.startTime()), parseNullableDateTime(request.endTime()), request.content(),
                request.nursingAdvice(), Boolean.TRUE.equals(request.abnormalFlag()) ? 1 : 0, currentUser.userId());
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        changeOrderStatus(currentUser.userId(), order, WAIT_REPORT, "CREATE_SERVICE_RECORD");
        return new ServiceRecordResponse(recordId, orderId, WAIT_REPORT);
    }

    @Transactional
    public ServiceRecordResponse createVitalSign(CurrentUser currentUser, String orderId, VitalSignRequest request) {
        Map<String, Object> task = requireTaskByOrderAndUser(orderId, currentUser);
        String vitalId = nextId("vital");
        jdbcTemplate.update("""
                INSERT INTO vital_sign_record
                  (vital_id, order_id, task_id, nurse_id, measured_at, temperature, pulse,
                   breath_rate, systolic_pressure, diastolic_pressure, blood_oxygen, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, vitalId, orderId, string(task, "task_id"), string(task, "nurse_id"),
                parseDateTime(request.startTime()), request.temperature(), request.pulse(), request.breathRate(),
                request.systolicPressure(), request.diastolicPressure(), request.bloodOxygen(), request.remark());
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        changeOrderStatus(currentUser.userId(), order, WAIT_REPORT, "CREATE_VITAL_SIGN");
        return new ServiceRecordResponse(vitalId, orderId, WAIT_REPORT);
    }

    public List<Map<String, Object>> serviceRecords(CurrentUser currentUser, String orderId) {
        orderDetail(currentUser, orderId);
        return jdbcTemplate.queryForList("""
                SELECT record_id AS recordId, order_id AS orderId, start_time AS startTime,
                       end_time AS endTime, content, nursing_advice AS nursingAdvice,
                       abnormal_flag AS abnormalFlag
                FROM care_service_record
                WHERE order_id = ?
                ORDER BY start_time DESC
                """, orderId);
    }

    @Transactional
    public ReportResponse generateReport(CurrentUser currentUser, String orderId) {
        orderDetail(currentUser, orderId);
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        String reportId = existingReportId(orderId);
        if (reportId == null) {
            reportId = nextId("report");
            String summary = "Service completed. Care records and vital signs are ready.";
            String advice = latestAdvice(orderId);
            jdbcTemplate.update("""
                    INSERT INTO service_report
                      (report_id, order_id, report_status, summary, nursing_advice, generated_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, reportId, orderId, WAIT_CONFIRM, summary, advice, currentUser.userId());
            insertReportItems(reportId, orderId, advice);
        }
        changeOrderStatus(currentUser.userId(), order, WAIT_CONFIRM, "GENERATE_SERVICE_REPORT");
        return report(orderId);
    }

    public ReportResponse report(String orderId) {
        Map<String, Object> report = requireRow("SELECT * FROM service_report WHERE order_id = ?", orderId);
        List<String> serviceRecords = jdbcTemplate.query("""
                SELECT item_content FROM service_report_item
                WHERE report_id = ? AND item_type = 'SERVICE_RECORD'
                ORDER BY sort
                """, (rs, rowNum) -> rs.getString("item_content"), string(report, "report_id"));
        List<String> vitalSigns = jdbcTemplate.query("""
                SELECT item_content FROM service_report_item
                WHERE report_id = ? AND item_type = 'VITAL_SIGN'
                ORDER BY sort
                """, (rs, rowNum) -> rs.getString("item_content"), string(report, "report_id"));
        return new ReportResponse(
                string(report, "report_id"),
                orderId,
                string(report, "summary"),
                vitalSigns,
                serviceRecords,
                string(report, "nursing_advice")
        );
    }

    @Transactional
    public OrderChangeResponse cancelFamilyOrder(CurrentUser currentUser, String orderId, OrderChangeRequest request) {
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        if (!currentUser.userId().equals(string(order, "family_id"))) {
            throw new ForbiddenException();
        }
        changeOrderStatus(currentUser.userId(), order, CANCELED, request.reason());
        return new OrderChangeResponse(orderId, CANCELED, toText(order.get("scheduled_start_at")));
    }

    @Transactional
    public OrderChangeResponse rescheduleFamilyOrder(CurrentUser currentUser, String orderId, OrderChangeRequest request) {
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        if (!currentUser.userId().equals(string(order, "family_id"))) {
            throw new ForbiddenException();
        }
        LocalDateTime scheduledStart = parseDateTime(request.newScheduledStart());
        jdbcTemplate.update("UPDATE nursing_order SET scheduled_start_at = ? WHERE order_id = ?", scheduledStart, orderId);
        saveStatusLog(orderId, string(order, "order_status"), string(order, "order_status"), currentUser.userId(), request.reason());
        return new OrderChangeResponse(orderId, string(order, "order_status"), scheduledStart.toString());
    }

    @Transactional
    public OrderChangeResponse cancelAdminOrder(CurrentUser currentUser, String orderId, OrderChangeRequest request) {
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        changeOrderStatus(currentUser.userId(), order, CANCELED, request.reason());
        return new OrderChangeResponse(orderId, CANCELED, toText(order.get("scheduled_start_at")));
    }

    public DemoDataStatusResponse demoDataStatus() {
        List<String> accounts = jdbcTemplate.query("""
                SELECT username FROM sys_user
                WHERE username IN ('elder_demo','family_demo','nurse_demo','admin_demo','cs_demo')
                ORDER BY username
                """, (rs, rowNum) -> rs.getString("username"));
        int scenarioCount = 0;
        scenarioCount += count("SELECT COUNT(*) FROM elder_family_binding WHERE binding_status = 'ACTIVE'");
        scenarioCount += count("SELECT COUNT(*) FROM service_item");
        scenarioCount += count("SELECT COUNT(*) FROM nursing_order");
        scenarioCount += count("SELECT COUNT(*) FROM nurse_task");
        return new DemoDataStatusResponse(accounts.size() == 5 && scenarioCount >= 4, accounts, scenarioCount);
    }

    private PageData<OrderDetailResponse> orderPage(String where, List<Object> args, int page, int size) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + orderFromSql() + " " + where,
                Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(offset(page, size));
        List<OrderDetailResponse> records = jdbcTemplate.query(orderDetailSql() + " " + where
                        + " ORDER BY o.scheduled_start_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new OrderDetailResponse(
                        rs.getString("order_id"),
                        rs.getString("order_id"),
                        rs.getString("order_status"),
                        rs.getString("elder_id"),
                        rs.getString("family_id"),
                        rs.getString("service_id"),
                        rs.getString("service_name"),
                        rs.getString("address_id"),
                        toText(rs.getObject("scheduled_start_at")),
                        toText(rs.getObject("scheduled_end_at")),
                        rs.getInt("service_price_cent"),
                        rs.getString("contact_name"),
                        rs.getString("contact_phone"),
                        rs.getString("remark")
                ), queryArgs.toArray());
        return new PageData<>(records, total == null ? 0 : total, page, size);
    }

    private String orderDetailSql() {
        return """
                SELECT o.order_id, o.order_status, o.elder_id, o.family_id, o.service_id,
                       si.service_name, o.address_id, o.scheduled_start_at, o.scheduled_end_at,
                       o.service_price_cent, o.contact_name, o.contact_phone, o.remark
                """ + orderFromSql();
    }

    private String orderFromSql() {
        return """
                FROM nursing_order o
                JOIN service_item si ON si.service_id = o.service_id
                JOIN elder_profile e ON e.elder_id = o.elder_id
                """;
    }

    private OrderDetailResponse mapOrderDetail(Map<String, Object> row) {
        return new OrderDetailResponse(
                string(row, "order_id"),
                string(row, "order_id"),
                string(row, "order_status"),
                string(row, "elder_id"),
                string(row, "family_id"),
                string(row, "service_id"),
                string(row, "service_name"),
                string(row, "address_id"),
                toText(row.get("scheduled_start_at")),
                toText(row.get("scheduled_end_at")),
                integer(row, "service_price_cent"),
                string(row, "contact_name"),
                string(row, "contact_phone"),
                string(row, "remark")
        );
    }

    private String taskSql() {
        return """
                SELECT nt.task_id, nt.order_id, nt.nurse_id, nt.task_status, nt.dispatch_remark,
                       o.order_status, o.scheduled_start_at
                FROM nurse_task nt
                JOIN nursing_order o ON o.order_id = nt.order_id
                """;
    }

    private boolean taskBelongsToNurse(String orderId, String nurseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM nurse_task WHERE order_id = ? AND nurse_id = ?
                """, Integer.class, orderId, nurseId);
        return count != null && count > 0;
    }

    private void requireRoleUser(String userId, String roleCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id
                WHERE ur.user_id = ? AND r.role_code = ?
                """, Integer.class, userId, roleCode);
        if (count == null || count == 0) {
            throw new BusinessRuleException();
        }
    }

    private void requireNurseTaskAccess(CurrentUser currentUser, Map<String, Object> task) {
        if (currentUser.hasRole(RoleCode.ADMIN) || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE)) {
            return;
        }
        if (currentUser.hasRole(RoleCode.NURSE) && currentUser.userId().equals(string(task, "nurse_id"))) {
            return;
        }
        throw new ForbiddenException();
    }

    private Map<String, Object> requireTaskByOrderAndUser(String orderId, CurrentUser currentUser) {
        Map<String, Object> task = requireRow("SELECT * FROM nurse_task WHERE order_id = ?", orderId);
        requireNurseTaskAccess(currentUser, task);
        return task;
    }

    private String existingReportId(String orderId) {
        try {
            return jdbcTemplate.queryForObject("SELECT report_id FROM service_report WHERE order_id = ?", String.class, orderId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String latestAdvice(String orderId) {
        try {
            String advice = jdbcTemplate.queryForObject("""
                    SELECT nursing_advice FROM care_service_record
                    WHERE order_id = ? AND nursing_advice IS NOT NULL
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, String.class, orderId);
            return advice == null ? "Continue observation and follow medical advice." : advice;
        } catch (EmptyResultDataAccessException exception) {
            return "Continue observation and follow medical advice.";
        }
    }

    private void insertReportItems(String reportId, String orderId, String advice) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT record_id, content FROM care_service_record
                WHERE order_id = ?
                ORDER BY start_time
                """, orderId);
        int sort = 1;
        for (Map<String, Object> record : records) {
            jdbcTemplate.update("""
                    INSERT INTO service_report_item
                      (item_id, report_id, item_type, item_title, item_content, source_id, sort)
                    VALUES (?, ?, 'SERVICE_RECORD', 'Service record', ?, ?, ?)
                    """, nextId("item"), reportId, string(record, "content"), string(record, "record_id"), sort++);
        }

        List<Map<String, Object>> vitals = jdbcTemplate.queryForList("""
                SELECT vital_id, temperature, pulse, systolic_pressure, diastolic_pressure, blood_oxygen
                FROM vital_sign_record
                WHERE order_id = ?
                ORDER BY measured_at
                """, orderId);
        for (Map<String, Object> vital : vitals) {
            String content = "temperature " + vital.get("temperature")
                    + ", pulse " + vital.get("pulse")
                    + ", blood pressure " + vital.get("systolic_pressure") + "/" + vital.get("diastolic_pressure")
                    + ", blood oxygen " + vital.get("blood_oxygen");
            jdbcTemplate.update("""
                    INSERT INTO service_report_item
                      (item_id, report_id, item_type, item_title, item_content, source_id, sort)
                    VALUES (?, ?, 'VITAL_SIGN', 'Vital sign', ?, ?, ?)
                    """, nextId("item"), reportId, content, string(vital, "vital_id"), sort++);
        }

        jdbcTemplate.update("""
                INSERT INTO service_report_item
                  (item_id, report_id, item_type, item_title, item_content, source_id, sort)
                VALUES (?, ?, 'NURSING_ADVICE', 'Nursing advice', ?, NULL, ?)
                """, nextId("item"), reportId, advice, sort);
    }

    private void changeOrderStatus(String userId, Map<String, Object> order, String targetStatus, String reason) {
        String sourceStatus = string(order, "order_status");
        if (sourceStatus.equals(targetStatus)) {
            return;
        }
        validateOrderTransition(sourceStatus, targetStatus);
        jdbcTemplate.update("UPDATE nursing_order SET order_status = ? WHERE order_id = ?",
                targetStatus, string(order, "order_id"));
        saveStatusLog(string(order, "order_id"), sourceStatus, targetStatus, userId, reason);
    }

    private void validateOrderTransition(String sourceStatus, String targetStatus) {
        boolean allowed = switch (sourceStatus) {
            case WAIT_DISPATCH -> List.of(DISPATCHED, CANCELED).contains(targetStatus);
            case DISPATCHED -> List.of(ACCEPTED, CANCELED).contains(targetStatus);
            case ACCEPTED -> List.of(ON_THE_WAY, CANCELED).contains(targetStatus);
            case ON_THE_WAY -> List.of(SERVING, CANCELED).contains(targetStatus);
            case SERVING -> List.of(WAIT_REPORT, CANCELED).contains(targetStatus);
            case WAIT_REPORT -> List.of(WAIT_CONFIRM, CANCELED).contains(targetStatus);
            case WAIT_CONFIRM -> List.of(COMPLETED, CANCELED).contains(targetStatus);
            default -> CANCELED.equals(targetStatus);
        };
        if (!allowed) {
            throw new ConflictException();
        }
    }

    private void requireStatus(Map<String, Object> order, String status) {
        if (!status.equals(string(order, "order_status"))) {
            throw new ConflictException();
        }
    }

    private void requireTaskStatus(Map<String, Object> task, String status) {
        if (!status.equals(string(task, "task_status"))) {
            throw new ConflictException();
        }
    }

    private void saveStatusLog(String orderId, String fromStatus, String toStatus, String changedBy, String reason) {
        jdbcTemplate.update("""
                INSERT INTO order_status_log
                  (status_log_id, order_id, from_status, to_status, changed_by, change_reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, nextId("log"), orderId, fromStatus, toStatus, changedBy, reason);
    }

    private void saveOperationLog(
            CurrentUser currentUser,
            String operationType,
            String bizType,
            String bizId,
            Object beforeValue,
            Object afterValue) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id,
                   before_value, after_value, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, nextId("op"), currentUser.userId(), currentUser.primaryRole(), operationType, bizType, bizId,
                writeJson(beforeValue), writeJson(afterValue), nextId("trace"));
    }

    private String normalizeServiceStatus(String status) {
        if (!hasText(status) || "ACTIVE".equals(status) || "ON_SHELF".equals(status)) {
            return "ON_SHELF";
        }
        if ("OFFLINE".equals(status) || "OFF_SHELF".equals(status)) {
            return "OFF_SHELF";
        }
        throw new BusinessRuleException();
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private int offset(int page, int size) {
        return (Math.max(page, 1) - 1) * Math.max(size, 1);
    }

    private <T> T queryOne(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return mapper.map(jdbcTemplate.queryForMap(sql, args));
        } catch (EmptyResultDataAccessException exception) {
            throw new NotFoundException();
        }
    }

    private Map<String, Object> requireRow(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException exception) {
            throw new NotFoundException();
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value);
        }
    }

    private LocalDateTime parseNullableDateTime(String value) {
        return hasText(value) ? parseDateTime(value) : null;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON serialization failed", exception);
        }
    }

    private String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Integer integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.parseInt(value.toString());
    }

    private String toText(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private interface RowMapper<T> {
        T map(Map<String, Object> row);
    }
}
