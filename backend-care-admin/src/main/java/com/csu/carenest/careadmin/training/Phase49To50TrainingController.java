package com.csu.carenest.careadmin.training;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 阶段49-50培训文章和护理端推荐阅读接口。 */
@RestController
@RequestMapping("/api/v1")
public class Phase49To50TrainingController {

    private final AuthService authService;
    private final Phase49To50TrainingService trainingService;

    public Phase49To50TrainingController(AuthService authService, Phase49To50TrainingService trainingService) {
        this.authService = authService;
        this.trainingService = trainingService;
    }

    @GetMapping("/admin/training-articles")
    public ApiResponse<List<TrainingDtos.ArticleResponse>> articles(
            @RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(trainingService.articles(adminUser(authorization)));
    }

    @PostMapping("/admin/training-articles")
    public ApiResponse<TrainingDtos.ArticleResponse> createArticle(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody TrainingDtos.ArticleRequest request) {
        return ApiResponse.success(trainingService.createArticle(adminUser(authorization), request));
    }

    @PutMapping("/admin/training-articles/{articleId}")
    public ApiResponse<TrainingDtos.ArticleResponse> updateArticle(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("articleId") String articleId,
            @Valid @RequestBody TrainingDtos.ArticleRequest request) {
        return ApiResponse.success(trainingService.updateArticle(
                adminUser(authorization), articleId, request));
    }

    @PostMapping("/admin/training-articles/{articleId}/publish")
    public ApiResponse<TrainingDtos.ArticleResponse> publishArticle(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("articleId") String articleId,
            @Valid @RequestBody TrainingDtos.ArticleRequest request) {
        return ApiResponse.success(trainingService.publishArticle(
                adminUser(authorization), articleId, request));
    }

    @GetMapping("/nurse/orders/{orderId}/recommended-articles")
    public ApiResponse<List<TrainingDtos.ReadResponse>> recommendedArticles(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(trainingService.recommendedArticles(user, orderId));
    }

    @PostMapping("/nurse/articles/{articleId}/read")
    public ApiResponse<TrainingDtos.ReadResponse> markRead(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("articleId") String articleId,
            @Valid @RequestBody TrainingDtos.ReadRequest request) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(trainingService.markRead(user, articleId, request));
    }

    private CurrentUser adminUser(String authorization) {
        return authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
    }
}
