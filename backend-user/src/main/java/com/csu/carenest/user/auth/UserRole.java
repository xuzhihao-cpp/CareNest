package com.csu.carenest.user.auth;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_role")
public class UserRole {

    private String userId;
    private String roleId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }
}
