package com.csu.carenest.user.auth;

import java.util.List;

public record AuthResponse(
        String token,
        String userId,
        String displayName,
        List<String> roles,
        List<String> menus
) {
}
