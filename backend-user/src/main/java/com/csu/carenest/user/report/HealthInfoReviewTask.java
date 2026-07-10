package com.csu.carenest.user.report;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("health_info_review_task")
public class HealthInfoReviewTask {

    @TableId("review_task_id")
    private String reviewTaskId;
    private String reportId;
    private String reviewStatus;
    private String reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewRemark;

    public String getReviewTaskId() { return reviewTaskId; }
    public String getReportId() { return reportId; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
}
