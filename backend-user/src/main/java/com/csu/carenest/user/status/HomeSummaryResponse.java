package com.csu.carenest.user.status;

import java.util.List;

public record HomeSummaryResponse(List<HomeCard> cards, List<HomeQuickAction> quickActions, int todoCount) {
    public record HomeCard(String key, String label, String value, String unit, String trend) {
    }

    public record HomeQuickAction(String key, String label, String path, String permissionCode) {
    }
}
