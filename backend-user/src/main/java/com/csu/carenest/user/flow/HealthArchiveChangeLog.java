package com.csu.carenest.user.flow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("health_archive_change_log")
public class HealthArchiveChangeLog {

    @TableId("change_log_id")
    private String changeLogId;
    private String elderId;
    private String changedBy;
    private String changeType;
    private String beforeValue;
    private String afterValue;

    public String getChangeLogId() {
        return changeLogId;
    }

    public void setChangeLogId(String changeLogId) {
        this.changeLogId = changeLogId;
    }

    public String getElderId() {
        return elderId;
    }

    public void setElderId(String elderId) {
        this.elderId = elderId;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
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
}
