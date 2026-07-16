package com.csu.carenest.careadmin.training;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 阶段49-50培训文章管理、推荐和阅读冻结字段。 */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    public record ArticleRequest(
            @NotBlank @Size(max = 128) String title,
            @Size(max = 500) String summary,
            @Size(max = 255) String contentUrl,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 64) String> tags,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 32) String> serviceIds,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 64) String> riskTags,
            @NotNull Boolean requiredRead,
            @NotBlank String status) {
    }

    public record ArticleResponse(String articleId, String status) {
    }

    public record ReadRequest(
            @NotBlank @Size(max = 32) String orderId,
            @NotNull @Min(0) @Max(86400) Integer readDurationSeconds) {
    }

    public record ReadResponse(String articleId, String readStatus) {
    }
}
