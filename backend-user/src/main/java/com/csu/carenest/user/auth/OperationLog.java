package com.csu.carenest.user.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.List;
import java.util.UUID;

@TableName("operation_log")
public class OperationLog {

    @TableId("log_id")
    private String logId;
    private String operatorId;
    private String roleCode;
    private String operationType;
    private String bizType;
    private String bizId;
    private String beforeValue;
    private String afterValue;
    private String traceId;

    public OperationLog() {
    }

    public OperationLog(String operatorId, String action, String targetId, List<String> permissionCodes) {
        this.logId = UUID.randomUUID().toString().replace("-", "");
        this.operatorId = operatorId;
        this.roleCode = targetId;
        this.operationType = action;
        this.bizType = "ROLE_PERMISSION";
        this.bizId = targetId;
        this.afterValue = toPermissionJson(permissionCodes);
        this.traceId = "auth-" + UUID.randomUUID().toString().replace("-", "");
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    private static String toPermissionJson(List<String> permissionCodes) {
        String values = permissionCodes.stream()
                .map(OperationLog::quoteJson)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "{\"permissionCodes\":[" + values + "]}";
    }

    private static String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
