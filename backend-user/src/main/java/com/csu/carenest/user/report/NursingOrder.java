package com.csu.carenest.user.report;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("nursing_order")
public class NursingOrder {

    @TableId("order_id")
    private String orderId;
    private String elderId;
    private String familyId;
    private String orderStatus;

    public String getOrderId() { return orderId; }
    public String getElderId() { return elderId; }
    public String getFamilyId() { return familyId; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
}
