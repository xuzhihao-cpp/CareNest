package com.csu.carenest.user.flow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("elder_family_binding")
public class ElderFamilyBinding {

    @TableId("binding_id")
    private String bindingId;
    private String elderId;
    private String familyId;
    private String bindingStatus;
    private String scopeCodes;
    private String relationType;
    private String inviterUserId;
    private String approverUserId;
    private String remark;

    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }

    public String getElderId() {
        return elderId;
    }

    public void setElderId(String elderId) {
        this.elderId = elderId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }

    public String getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(String bindingStatus) {
        this.bindingStatus = bindingStatus;
    }

    public String getScopeCodes() {
        return scopeCodes;
    }

    public void setScopeCodes(String scopeCodes) {
        this.scopeCodes = scopeCodes;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getInviterUserId() {
        return inviterUserId;
    }

    public void setInviterUserId(String inviterUserId) {
        this.inviterUserId = inviterUserId;
    }

    public String getApproverUserId() {
        return approverUserId;
    }

    public void setApproverUserId(String approverUserId) {
        this.approverUserId = approverUserId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
