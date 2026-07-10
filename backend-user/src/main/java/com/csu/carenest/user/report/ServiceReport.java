package com.csu.carenest.user.report;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("service_report")
public class ServiceReport {

    @TableId("report_id")
    private String reportId;
    private String orderId;
    private String reportStatus;
    private LocalDateTime confirmedAt;

    public String getReportId() { return reportId; }
    public String getOrderId() { return orderId; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
