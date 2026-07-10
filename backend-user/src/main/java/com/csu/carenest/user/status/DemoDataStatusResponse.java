package com.csu.carenest.user.status;

import java.util.List;

public record DemoDataStatusResponse(boolean ready, List<String> accounts, int scenarioCount) {
}
