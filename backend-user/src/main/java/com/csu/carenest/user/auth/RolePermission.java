package com.csu.carenest.user.auth;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role_permission")
public class RolePermission {

    private String roleId;
    private String permissionId;
    private Integer sort;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
