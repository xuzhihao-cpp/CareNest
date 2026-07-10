package com.csu.carenest.user.report;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("care_report_ack")
public class CareReportAck {

    @TableId("ack_id")
    private String ackId;
    private String reportId;
    private String orderId;
    private String ackUserId;
    private String ackRole;
    private String ackResult;
    private Integer satisfaction;
    private String remark;
    private String acceptedSuggestionIds;

    public String getAckId() { return ackId; }
    public void setAckId(String ackId) { this.ackId = ackId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setAckUserId(String ackUserId) { this.ackUserId = ackUserId; }
    public void setAckRole(String ackRole) { this.ackRole = ackRole; }
    public void setAckResult(String ackResult) { this.ackResult = ackResult; }
    public void setSatisfaction(Integer satisfaction) { this.satisfaction = satisfaction; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setAcceptedSuggestionIds(String acceptedSuggestionIds) { this.acceptedSuggestionIds = acceptedSuggestionIds; }
}
