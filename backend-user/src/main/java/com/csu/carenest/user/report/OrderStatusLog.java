package com.csu.carenest.user.report;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("order_status_log")
public class OrderStatusLog {

    @TableId("status_log_id")
    private String statusLogId;
    private String orderId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private String changeReason;

    public void setStatusLogId(String statusLogId) { this.statusLogId = statusLogId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
