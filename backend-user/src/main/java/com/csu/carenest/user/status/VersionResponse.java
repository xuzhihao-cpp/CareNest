package com.csu.carenest.user.status;

public record VersionResponse(String gitCommit, String buildTime, String apiPrefix) {
}
