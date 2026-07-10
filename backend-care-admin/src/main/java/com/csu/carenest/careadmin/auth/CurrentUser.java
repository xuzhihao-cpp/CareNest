package com.csu.carenest.careadmin.auth;

import java.util.List;

/**
 * 当前请求用户的轻量身份对象，供业务层做角色和数据归属校验。
 */
public record CurrentUser(String userId, List<RoleCode> roles) {

    public boolean hasRole(RoleCode roleCode) {
        return roles.contains(roleCode);
    }

    public String primaryRole() {
        return roles.isEmpty() ? null : roles.get(0).name();
    }
}
