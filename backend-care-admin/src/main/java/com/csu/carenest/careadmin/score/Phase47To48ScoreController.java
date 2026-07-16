package com.csu.carenest.careadmin.score;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 阶段47-48评分重算、查询和护理端展示接口。 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class Phase47To48ScoreController {

    private final AuthService authService;
    private final Phase47To48ScoreService scoreService;

    public Phase47To48ScoreController(AuthService authService, Phase47To48ScoreService scoreService) {
        this.authService = authService;
        this.scoreService = scoreService;
    }

    @PostMapping("/admin/nurses/{nurseId}/score/recalculate")
    public ApiResponse<ScoreDtos.ScoreResponse> recalculate(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("nurseId") String nurseId,
            @Valid @RequestBody ScoreDtos.RecalculateRequest request) {
        return ApiResponse.success(scoreService.recalculate(
                adminUser(authorization), nurseId, request));
    }

    @GetMapping("/nurses/{nurseId}/score")
    public ApiResponse<ScoreDtos.ScoreResponse> score(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("nurseId") String nurseId) {
        return ApiResponse.success(scoreService.score(
                authService.requireCurrentUser(authorization), nurseId, 20));
    }

    @GetMapping("/nurses/{nurseId}/score-logs")
    public ApiResponse<ScoreDtos.ScoreResponse> scoreLogs(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("nurseId") String nurseId) {
        return ApiResponse.success(scoreService.score(
                authService.requireCurrentUser(authorization), nurseId, 100));
    }

    @GetMapping("/nurse/my-score")
    public ApiResponse<ScoreDtos.MyScoreResponse> myScore(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("page") @Min(1) int page,
            @RequestParam("size") @Min(1) @Max(100) int size) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(scoreService.myScore(user, page, size));
    }

    @GetMapping("/nurse/my-score/change-logs")
    public ApiResponse<ScoreDtos.MyScoreResponse> myScoreChangeLogs(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("page") @Min(1) int page,
            @RequestParam("size") @Min(1) @Max(100) int size) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(scoreService.myScore(user, page, size));
    }

    private CurrentUser adminUser(String authorization) {
        return authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
    }
}
