package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.csu.carenest.careadmin.common.PageData;
import com.csu.carenest.careadmin.phase.dto.*;
import com.csu.carenest.careadmin.redis.RedisCacheService;
import com.csu.carenest.careadmin.redis.RedisKeyFactory;
import com.csu.carenest.careadmin.redis.RedisLockService;
import com.csu.carenest.careadmin.redis.HomeCacheInvalidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

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
    private static final String REJECTED = "REJECTED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheService cacheService;
    private final RedisLockService lockService;
    private final HomeCacheInvalidator homeCacheInvalidator;

    public CareAdminPhaseService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RedisCacheService cacheService,
            RedisLockService lockService,
            HomeCacheInvalidator homeCacheInvalidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
        this.lockService = lockService;
        this.homeCacheInvalidator = homeCacheInvalidator;
    }

    public HomeSummaryResponse nurseWorkbenchSummary(CurrentUser currentUser) {
        String cacheKey = RedisKeyFactory.homeKey(RoleCode.NURSE.name(), currentUser.userId());
        return cacheService.get(cacheKey, HomeSummaryResponse.class)
                .orElseGet(() -> {
                    HomeSummaryResponse summary = loadNurseWorkbenchSummary(currentUser);
                    cacheService.put(cacheKey, summary, Duration.ofSeconds(30));
                    return summary;
                });
    }

    private HomeSummaryResponse loadNurseWorkbenchSummary(CurrentUser currentUser) {
        int pending = count("SELECT COUNT(*) FROM nurse_task WHERE nurse_id = ? AND task_status IN ('DISPATCHED', 'ACCEPTED')",
                currentUser.userId());
        int serving = count("SELECT COUNT(*) FROM nurse_task WHERE nurse_id = ? AND task_status IN ('ON_THE_WAY', 'SERVING')",
                currentUser.userId());
        int report = count("SELECT COUNT(*) FROM nursing_order o JOIN nurse_task nt ON nt.order_id = o.order_id "
                + "WHERE nt.nurse_id = ? AND o.order_status = 'WAIT_REPORT'", currentUser.userId());
        return new HomeSummaryResponse(
                List.of(
                        new HomeSummaryResponse.HomeCard("pending", "待处理任务", String.valueOf(pending), "项", "实时"),
                        new HomeSummaryResponse.HomeCard("serving", "进行中服务", String.valueOf(serving), "项", "实时"),
                        new HomeSummaryResponse.HomeCard("report", "待提交报告", String.valueOf(report), "项", "需处理")),
                List.of(new HomeSummaryResponse.HomeQuickAction("tasks", "查看护理任务", "/pages/nurse/index", "nurse:task:view")),
                pending + report);
    }

    public HomeSummaryResponse adminDashboardOverview(CurrentUser currentUser) {
        String cacheKey = RedisKeyFactory.homeKey(currentUser.primaryRole(), currentUser.userId());
        return cacheService.get(cacheKey, HomeSummaryResponse.class)
                .orElseGet(() -> {
                    HomeSummaryResponse summary = loadAdminDashboardOverview();
                    cacheService.put(cacheKey, summary, Duration.ofSeconds(30));
                    return summary;
                });
    }

    private HomeSummaryResponse loadAdminDashboardOverview() {
        int waitingDispatch = count("SELECT COUNT(*) FROM nursing_order WHERE order_status = 'WAIT_DISPATCH'");
        int active = count("SELECT COUNT(*) FROM nursing_order WHERE order_status IN ('DISPATCHED', 'ACCEPTED', 'ON_THE_WAY', 'SERVING')");
        int waitingConfirm = count("SELECT COUNT(*) FROM nursing_order WHERE order_status = 'WAIT_CONFIRM'");
        return new HomeSummaryResponse(
                List.of(
                        new HomeSummaryResponse.HomeCard("dispatch", "待派单订单", String.valueOf(waitingDispatch), "单", "需处理"),
                        new HomeSummaryResponse.HomeCard("active", "服务中订单", String.valueOf(active), "单", "实时"),
                        new HomeSummaryResponse.HomeCard("confirm", "待确认报告", String.valueOf(waitingConfirm), "单", "待跟进")),
                List.of(new HomeSummaryResponse.HomeQuickAction("orders", "订单管理", "/pages/admin/index", "order:manage")),
                waitingDispatch + waitingConfirm);
    }

    public List<ServiceItemResponse> serviceItems(boolean includeOffShelf) {
        if (!includeOffShelf) {
            return cacheService.get(RedisKeyFactory.onShelfServiceItemsKey(), ServiceItemResponse[].class)
                    .map(Arrays::asList)
                    .orElseGet(() -> {
                        List<ServiceItemResponse> items = loadServiceItems(false);
                        cacheService.put(RedisKeyFactory.onShelfServiceItemsKey(),
                                items.toArray(ServiceItemResponse[]::new), Duration.ofMinutes(5));
                        return items;
                    });
        }
        return loadServiceItems(true);
    }

    private List<ServiceItemResponse> loadServiceItems(boolean includeOffShelf) {
        String sql = """
                SELECT service_id, service_name, service_desc, price_cent, duration_minutes, service_status
                FROM service_item
                """ + (includeOffShelf ? "" : " WHERE service_status = 'ON_SHELF' ") + " ORDER BY sort, service_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ServiceItemResponse(
                rs.getString("service_id"),
                rs.getString("service_name"),
                rs.getString("service_desc"),
                rs.getInt("price_cent"),
                rs.getInt("duration_minutes"),
                rs.getString("service_status")
        ));
    }

    public ServiceItemResponse serviceItem(String serviceId) {
        return queryOne("""
                SELECT service_id, service_name, service_desc, price_cent, duration_minutes, service_status
                FROM service_item
                WHERE service_id = ?
                """, row -> new ServiceItemResponse(
                string(row, "service_id"),
                string(row, "service_name"),
                string(row, "service_desc"),
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
        evictOnShelfServiceItemsAfterCommit();
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
        evictOnShelfServiceItemsAfterCommit();
        return serviceItem(serviceId);
    }

    @Transactional
    public ServiceItemResponse deleteServiceItem(CurrentUser currentUser, String serviceId) {
        ServiceItemResponse serviceItem = serviceItem(serviceId);
        int orderCount = count("SELECT COUNT(*) FROM nursing_order WHERE service_id = ?", serviceId);
        if (orderCount > 0) {
            throw new ConflictException();
        }
        jdbcTemplate.update("DELETE FROM service_item WHERE service_id = ?", serviceId);
        saveOperationLog(currentUser, "DELETE_SERVICE_ITEM", "SERVICE_ITEM", serviceId, serviceItem, null);
        evictOnShelfServiceItemsAfterCommit();
        return serviceItem;
    }

    public OrderDetailResponse orderDetail(CurrentUser currentUser, String orderId) {
        OrderDetailResponse detail = mapOrderDetail(requireRow(orderDetailSql() + " WHERE o.order_id = ?", orderId));
        if (currentUser.hasRole(RoleCode.ADMIN) || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE)) {
            return detail;
        }
        if (currentUser.hasRole(RoleCode.FAMILY) && currentUser.userId().equals(detail.familyId())) {
            return detail;
        }
        if (currentUser.hasRole(RoleCode.ELDER) && elderOwnsOrder(currentUser.userId(), detail.elderId())) {
            return detail;
        }
        if (currentUser.hasRole(RoleCode.NURSE) && taskBelongsToNurse(orderId, currentUser.userId())) {
            return detail;
        }
        throw new ForbiddenException();
    }

    private boolean elderOwnsOrder(String userId, String elderId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM elder_profile WHERE elder_id = ? AND user_id = ?", Integer.class, elderId, userId);
        return count != null && count > 0;
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

    public PageData<OrderDetailResponse> familyOrders(CurrentUser currentUser, int page, int size) {
        return orderPage("WHERE o.family_id = ?", List.of(currentUser.userId()), page, size);
    }

    @Transactional
    public OrderDetailResponse createFamilyOrder(CurrentUser currentUser, FamilyOrderRequest request) {
        if (!hasActiveScope(currentUser.userId(), request.elderId(), "ORDER_CREATE")) {
            throw new ForbiddenException();
        }
        Map<String, Object> address = requireRow("""
                SELECT * FROM service_address
                WHERE address_id = ? AND elder_id = ? AND family_id = ?
                """, request.addressId(), request.elderId(), currentUser.userId());
        Map<String, Object> service = requireRow("""
                SELECT * FROM service_item
                WHERE service_id = ? AND service_status = 'ON_SHELF'
                """, request.serviceId());
        LocalDateTime scheduledStart = parseDateTime(request.scheduledStart());
        if (!scheduledStart.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException();
        }
        String orderId = nextId("order");
        int duration = integer(service, "duration_minutes");
        String addressSnapshot = string(address, "region_code") + " " + string(address, "detail_address");
        jdbcTemplate.update("""
                INSERT INTO nursing_order
                  (order_id, elder_id, family_id, service_id, address_id, service_address_snapshot, order_status,
                   scheduled_start_at, scheduled_end_at, service_price_cent, contact_name,
                   contact_phone, remark, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderId, request.elderId(), currentUser.userId(), request.serviceId(), request.addressId(), addressSnapshot,
                WAIT_DISPATCH, scheduledStart, scheduledStart.plusMinutes(duration), integer(service, "price_cent"),
                string(address, "contact_name"), string(address, "contact_phone"), request.remark(), currentUser.userId());
        saveStatusLog(orderId, null, WAIT_DISPATCH, currentUser.userId(), "CREATE_FAMILY_ORDER");
        saveOperationLog(currentUser, "CREATE_FAMILY_ORDER", "NURSING_ORDER", orderId, null, request);
        Map<String, Object> createdOrder = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        evictOrderHomesAfterCommit(currentUser, createdOrder, null);
        return mapOrderDetail(requireRow(orderDetailSql() + " WHERE o.order_id = ?", orderId));
    }

    @Transactional
    public DispatchResponse dispatchOrder(CurrentUser currentUser, String orderId, DispatchRequest request) {
        return withOrderLock(orderId, () -> {
            Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
            requireStatus(order, WAIT_DISPATCH);
            requireRoleUser(request.nurseId(), "NURSE");
            String taskId = nextId("task");
            try {
                jdbcTemplate.update("""
                        INSERT INTO nurse_task (task_id, order_id, nurse_id, task_status, dispatch_remark)
                        VALUES (?, ?, ?, ?, ?)
                        """, taskId, orderId, request.nurseId(), DISPATCHED, request.dispatchRemark());
            } catch (DuplicateKeyException duplicate) {
                throw new ConflictException();
            }
            changeOrderStatus(currentUser.userId(), order, DISPATCHED, "DISPATCH_ORDER");
            saveOperationLog(currentUser, "DISPATCH_ORDER", "NURSE_TASK", taskId, null, request);
            evictOrderHomesAfterCommit(currentUser, order, request.nurseId());
            return new DispatchResponse(orderId, DISPATCHED, taskId);
        });
    }

    @Transactional
    public DispatchResponse acceptTask(CurrentUser currentUser, String taskId) {
        Map<String, Object> taskSnapshot = requireRow("SELECT * FROM nurse_task WHERE task_id = ?", taskId);
        requireNurseTaskAccess(currentUser, taskSnapshot);
        String orderId = string(taskSnapshot, "order_id");
        return withOrderLock(orderId, () -> {
            Map<String, Object> task = requireRow("SELECT * FROM nurse_task WHERE task_id = ?", taskId);
            requireNurseTaskAccess(currentUser, task);
            requireTaskStatus(task, DISPATCHED);
            int updated = jdbcTemplate.update("""
                    UPDATE nurse_task SET task_status = ?, accepted_at = CURRENT_TIMESTAMP
                    WHERE task_id = ? AND task_status = ?
                    """, ACCEPTED, taskId, DISPATCHED);
            if (updated != 1) {
                throw new ConflictException();
            }
            Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
            changeOrderStatus(currentUser.userId(), order, ACCEPTED, "ACCEPT_TASK");
            evictOrderHomesAfterCommit(currentUser, order, string(task, "nurse_id"));
            return new DispatchResponse(orderId, ACCEPTED, taskId);
        });
    }

    @Transactional
    public DispatchResponse updateTaskStatus(CurrentUser currentUser, String taskId, TaskStatusRequest request) {
        Map<String, Object> taskSnapshot = requireRow("SELECT * FROM nurse_task WHERE task_id = ?", taskId);
        requireNurseTaskAccess(currentUser, taskSnapshot);
        String orderId = string(taskSnapshot, "order_id");
        return withOrderLock(orderId, () -> updateTaskStatusLocked(currentUser, taskId, request));
    }

    private DispatchResponse updateTaskStatusLocked(CurrentUser currentUser, String taskId, TaskStatusRequest request) {
        Map<String, Object> task = requireRow("SELECT * FROM nurse_task WHERE task_id = ?", taskId);
        requireNurseTaskAccess(currentUser, task);
        String targetStatus = hasText(request.targetStatus()) ? request.targetStatus() : ON_THE_WAY;
        if (!List.of(ON_THE_WAY, SERVING, COMPLETED, CANCELED).contains(targetStatus)) {
            throw new BusinessRuleException();
        }

        String orderId = string(task, "order_id");
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        String currentOrderStatus = string(order, "order_status");
        if (COMPLETED.equals(targetStatus) && CANCELED.equals(currentOrderStatus)) {
            throw new ConflictException();
        }

        String timeColumn = switch (targetStatus) {
            case SERVING -> ", started_at = CURRENT_TIMESTAMP";
            case COMPLETED -> ", completed_at = CURRENT_TIMESTAMP";
            default -> "";
        };
        int updated = jdbcTemplate.update("UPDATE nurse_task SET task_status = ?" + timeColumn
                        + " WHERE task_id = ? AND task_status = ?",
                targetStatus, taskId, string(task, "task_status"));
        if (updated != 1) {
            throw new ConflictException();
        }

        // Historical records can contain a serving task whose order was already finalized.
        // Finish the task without attempting to move that finalized order backwards.
        if (COMPLETED.equals(targetStatus) && List.of(WAIT_CONFIRM, COMPLETED).contains(currentOrderStatus)) {
            evictOrderHomesAfterCommit(currentUser, order, string(task, "nurse_id"));
            return new DispatchResponse(orderId, currentOrderStatus, taskId);
        }
        String orderTargetStatus = COMPLETED.equals(targetStatus) ? WAIT_REPORT : targetStatus;
        changeOrderStatus(currentUser.userId(), order, orderTargetStatus, "UPDATE_TASK_STATUS");
        evictOrderHomesAfterCommit(currentUser, order, string(task, "nurse_id"));
        return new DispatchResponse(orderId, orderTargetStatus, taskId);
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
                        rs.getString("nurse_name"),
                        rs.getString("elder_name"),
                        rs.getString("service_name"),
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
                string(task, "nurse_name"),
                string(task, "elder_name"),
                string(task, "service_name"),
                string(task, "task_status"),
                string(task, "order_status"),
                string(task, "dispatch_remark"),
                toText(task.get("scheduled_start_at"))
        );
    }

    @Transactional
    public ServiceRecordResponse createServiceRecord(CurrentUser currentUser, String orderId, ServiceRecordRequest request) {
        requireTaskByOrderAndUser(orderId, currentUser);
        return withOrderLock(orderId, () -> createServiceRecordLocked(currentUser, orderId, request));
    }

    private ServiceRecordResponse createServiceRecordLocked(
            CurrentUser currentUser,
            String orderId,
            ServiceRecordRequest request) {
        Map<String, Object> task = requireTaskByOrderAndUser(orderId, currentUser);
        if (!COMPLETED.equals(string(task, "task_status"))) {
            throw new BusinessRuleException();
        }
        Integer existingRecords = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_service_record WHERE order_id = ?", Integer.class, orderId);
        if (existingRecords != null && existingRecords > 0) {
            throw new ConflictException();
        }
        String recordId = nextId("record");
        try {
            jdbcTemplate.update("""
                    INSERT INTO care_service_record
                      (record_id, order_id, task_id, nurse_id, start_time, end_time, content,
                       nursing_advice, abnormal_flag, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, recordId, orderId, string(task, "task_id"), string(task, "nurse_id"),
                    parseDateTime(request.startTime()), parseNullableDateTime(request.endTime()), request.content(),
                    request.nursingAdvice(), Boolean.TRUE.equals(request.abnormalFlag()) ? 1 : 0, currentUser.userId());
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException();
        }
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        changeOrderStatus(currentUser.userId(), order, WAIT_REPORT, "CREATE_SERVICE_RECORD");
        evictOrderHomesAfterCommit(currentUser, order, string(task, "nurse_id"));
        return new ServiceRecordResponse(recordId, orderId, WAIT_REPORT);
    }

    @Transactional
    public ServiceRecordResponse createVitalSign(CurrentUser currentUser, String orderId, VitalSignRequest request) {
        requireTaskByOrderAndUser(orderId, currentUser);
        return withOrderLock(orderId, () -> createVitalSignLocked(currentUser, orderId, request));
    }

    private ServiceRecordResponse createVitalSignLocked(
            CurrentUser currentUser,
            String orderId,
            VitalSignRequest request) {
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
        evictOrderHomesAfterCommit(currentUser, order, string(task, "nurse_id"));
        return new ServiceRecordResponse(vitalId, orderId, WAIT_REPORT);
    }

    public List<Map<String, Object>> serviceRecords(CurrentUser currentUser, String orderId) {
        OrderDetailResponse detail = orderDetail(currentUser, orderId);
        requireFamilyScope(currentUser, detail, "REPORT_VIEW");
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
        return withOrderLock(orderId, () -> generateReportLocked(currentUser, orderId));
    }

    private ReportResponse generateReportLocked(CurrentUser currentUser, String orderId) {
        Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        requireStatus(order, WAIT_REPORT);
        Integer recordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_service_record WHERE order_id = ?", Integer.class, orderId);
        if (recordCount == null || recordCount == 0) {
            throw new BusinessRuleException();
        }
        String reportId = existingReportId(orderId);
        String summary = "本次服务已完成，服务记录和生命体征信息已整理完成。";
        String advice = latestAdvice(orderId);
        if (reportId == null) {
            reportId = nextId("report");
            try {
                jdbcTemplate.update("""
                        INSERT INTO service_report
                          (report_id, order_id, report_status, summary, nursing_advice, generated_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, reportId, orderId, WAIT_CONFIRM, summary, advice, currentUser.userId());
            } catch (DuplicateKeyException duplicate) {
                throw new ConflictException();
            }
            insertReportItems(reportId, orderId, advice);
        } else {
            String lockedReportId = reportId;
            withReportLock(lockedReportId, () -> {
                int updated = jdbcTemplate.update("""
                        UPDATE service_report
                        SET report_status = ?, summary = ?, nursing_advice = ?, generated_by = ?,
                            generated_at = CURRENT_TIMESTAMP, confirmed_at = NULL
                        WHERE report_id = ? AND report_status = ?
                        """, WAIT_CONFIRM, summary, advice, currentUser.userId(), lockedReportId, REJECTED);
                if (updated != 1) {
                    throw new ConflictException();
                }
                jdbcTemplate.update("DELETE FROM service_report_item WHERE report_id = ?", lockedReportId);
                insertReportItems(lockedReportId, orderId, advice);
                return null;
            });
        }
        changeOrderStatus(currentUser.userId(), order, WAIT_CONFIRM, "GENERATE_SERVICE_REPORT");
        evictOrderHomesAfterCommit(currentUser, order, assignedNurseId(orderId));
        return report(orderId);
    }

    public ReportResponse report(CurrentUser currentUser, String orderId) {
        OrderDetailResponse detail = orderDetail(currentUser, orderId);
        requireFamilyScope(currentUser, detail, "REPORT_VIEW");
        return report(orderId);
    }

    private ReportResponse report(String orderId) {
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
        Map<String, Object> snapshot = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        requireFamilyOrderChangeAccess(currentUser, snapshot);
        return withOrderLock(orderId, () -> {
            Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
            requireFamilyOrderChangeAccess(currentUser, order);
            requireText(request.reason());
            changeOrderStatus(currentUser.userId(), order, CANCELED, request.reason());
            evictOrderHomesAfterCommit(currentUser, order, assignedNurseId(orderId));
            return new OrderChangeResponse(orderId, CANCELED, toText(order.get("scheduled_start_at")));
        });
    }

    @Transactional
    public OrderChangeResponse rescheduleFamilyOrder(CurrentUser currentUser, String orderId, OrderChangeRequest request) {
        Map<String, Object> snapshot = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        requireFamilyOrderChangeAccess(currentUser, snapshot);
        return withOrderLock(orderId, () -> {
            Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
            requireFamilyOrderChangeAccess(currentUser, order);
            requireText(request.reason());
            LocalDateTime scheduledStart = parseDateTime(request.newScheduledStart());
            Integer durationMinutes = jdbcTemplate.queryForObject(
                    "SELECT duration_minutes FROM service_item WHERE service_id = ?", Integer.class, string(order, "service_id"));
            int updated = jdbcTemplate.update("""
                    UPDATE nursing_order SET scheduled_start_at = ?, scheduled_end_at = ?
                    WHERE order_id = ? AND order_status = ? AND scheduled_start_at = ?
                    """, scheduledStart, scheduledStart.plusMinutes(durationMinutes == null ? 60 : durationMinutes),
                    orderId, string(order, "order_status"), order.get("scheduled_start_at"));
            if (updated != 1) {
                throw new ConflictException();
            }
            saveStatusLog(orderId, string(order, "order_status"), string(order, "order_status"),
                    currentUser.userId(), request.reason());
            evictOrderHomesAfterCommit(currentUser, order, assignedNurseId(orderId));
            return new OrderChangeResponse(orderId, string(order, "order_status"), scheduledStart.toString());
        });
    }

    @Transactional
    public OrderChangeResponse cancelAdminOrder(CurrentUser currentUser, String orderId, OrderChangeRequest request) {
        requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
        return withOrderLock(orderId, () -> {
            Map<String, Object> order = requireRow("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
            changeOrderStatus(currentUser.userId(), order, CANCELED, request.reason());
            evictOrderHomesAfterCommit(currentUser, order, assignedNurseId(orderId));
            return new OrderChangeResponse(orderId, CANCELED, toText(order.get("scheduled_start_at")));
        });
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
                        rs.getString("service_address"),
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
                       si.service_name, o.address_id,
                       COALESCE(o.service_address_snapshot, CONCAT(sa.region_code, ' ', sa.detail_address)) AS service_address,
                       o.scheduled_start_at, o.scheduled_end_at,
                       o.service_price_cent, o.contact_name, o.contact_phone, o.remark
                """ + orderFromSql();
    }

    private String orderFromSql() {
        return """
                FROM nursing_order o
                JOIN service_item si ON si.service_id = o.service_id
                JOIN elder_profile e ON e.elder_id = o.elder_id
                LEFT JOIN service_address sa ON sa.address_id = o.address_id
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
                string(row, "service_address"),
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
                       o.order_status, o.scheduled_start_at, service.service_name,
                       COALESCE(elder_user.display_name, '长辈') AS elder_name,
                       COALESCE(nurse_user.display_name, nt.nurse_id) AS nurse_name
                FROM nurse_task nt
                JOIN nursing_order o ON o.order_id = nt.order_id
                LEFT JOIN service_item service ON service.service_id = o.service_id
                LEFT JOIN elder_profile elder ON elder.elder_id = o.elder_id
                LEFT JOIN sys_user elder_user ON elder_user.user_id = elder.user_id
                LEFT JOIN sys_user nurse_user ON nurse_user.user_id = nt.nurse_id
                """;
    }

    private boolean taskBelongsToNurse(String orderId, String nurseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM nurse_task WHERE order_id = ? AND nurse_id = ?
                """, Integer.class, orderId, nurseId);
        return count != null && count > 0;
    }

    private boolean hasActiveScope(String familyId, String elderId, String scopeCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM elder_family_binding
                WHERE family_id = ? AND elder_id = ? AND binding_status = 'ACTIVE'
                  AND JSON_CONTAINS(scope_codes, JSON_QUOTE(?))
                """, Integer.class, familyId, elderId, scopeCode);
        return count != null && count > 0;
    }

    private void requireFamilyScope(CurrentUser currentUser, OrderDetailResponse detail, String scopeCode) {
        if (currentUser.hasRole(RoleCode.FAMILY)
                && !hasActiveScope(currentUser.userId(), detail.elderId(), scopeCode)) {
            throw new ForbiddenException();
        }
    }

    private void requireFamilyOrderChangeAccess(CurrentUser currentUser, Map<String, Object> order) {
        if (!currentUser.userId().equals(string(order, "family_id"))
                || !hasActiveScope(currentUser.userId(), string(order, "elder_id"), "ORDER_CREATE")) {
            throw new ForbiddenException();
        }
        if (!List.of(WAIT_DISPATCH, DISPATCHED, ACCEPTED, ON_THE_WAY).contains(string(order, "order_status"))) {
            throw new ConflictException();
        }
    }

    private void requireText(String value) {
        if (!hasText(value)) {
            throw new BusinessRuleException();
        }
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
        int updated = jdbcTemplate.update("""
                UPDATE nursing_order SET order_status = ?
                WHERE order_id = ? AND order_status = ?
                """, targetStatus, string(order, "order_id"), sourceStatus);
        if (updated != 1) {
            throw new ConflictException();
        }
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

    private void evictOnShelfServiceItemsAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheService.evict(RedisKeyFactory.onShelfServiceItemsKey());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheService.evict(RedisKeyFactory.onShelfServiceItemsKey());
            }
        });
    }

    private <T> T withOrderLock(String orderId, Supplier<T> action) {
        try (RedisLockService.Acquisition lock = lockService.tryAcquire(
                RedisKeyFactory.orderLockKey(orderId), Duration.ofSeconds(20))) {
            if (lock.contended()) {
                throw new ConflictException();
            }
            return action.get();
        }
    }

    private <T> T withReportLock(String reportId, Supplier<T> action) {
        try (RedisLockService.Acquisition lock = lockService.tryAcquire(
                RedisKeyFactory.reportLockKey(reportId), Duration.ofSeconds(20))) {
            if (lock.contended()) {
                throw new ConflictException();
            }
            return action.get();
        }
    }

    private void evictOrderHomesAfterCommit(
            CurrentUser operator,
            Map<String, Object> order,
            String nurseId) {
        homeCacheInvalidator.evictAfterCommit(operator.primaryRole(), operator.userId());
        String familyId = string(order, "family_id");
        if (hasText(familyId)) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), familyId);
        }
        String elderId = string(order, "elder_id");
        if (hasText(elderId)) {
            List<String> elderUsers = jdbcTemplate.query(
                    "SELECT user_id FROM elder_profile WHERE elder_id = ?",
                    (rs, rowNum) -> rs.getString("user_id"), elderId);
            for (String elderUser : elderUsers) {
                homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elderUser);
            }
        }
        if (hasText(nurseId)) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.NURSE.name(), nurseId);
        }
        List<Map<String, Object>> adminUsers = jdbcTemplate.queryForList("""
                SELECT ur.user_id, r.role_code
                FROM user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id
                WHERE r.role_code IN ('ADMIN', 'CUSTOMER_SERVICE')
                """);
        for (Map<String, Object> admin : adminUsers) {
            homeCacheInvalidator.evictAfterCommit(string(admin, "role_code"), string(admin, "user_id"));
        }
    }

    private String assignedNurseId(String orderId) {
        List<String> nurseIds = jdbcTemplate.query(
                "SELECT nurse_id FROM nurse_task WHERE order_id = ?",
                (rs, rowNum) -> rs.getString("nurse_id"), orderId);
        return nurseIds.isEmpty() ? null : nurseIds.get(0);
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

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
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
