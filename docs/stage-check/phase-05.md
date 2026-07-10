# Phase 05 验收记录：全端请求层与 mock 开关

## 阶段目标

封装统一 `request`、`ApiResponse`、`PageResult`、`FileUploadResult` 和 mock/真实接口切换。

## 验收清单

- [x] `frontend/src/api/client.ts` 提供统一 `request(method,url,data)` 能力。
- [x] `frontend/src/types/api.ts` 提供 `ApiResponse<T>`、`PageResult<T>`、`FileUploadResult`。
- [x] `frontend/src/api/mockServerPaths.ts` 维护阶段 1-4 mock path 清单。
- [x] 阶段 1-4 API 调用已接入统一请求层。
- [x] `VITE_USE_MOCK=true` 时页面使用同结构 mock。
- [x] `VITE_USE_MOCK=false` 且切换 `VITE_FRONTEND_API_BASE` 后，同一页面可调用真实接口形态服务。
- [x] mock 字段、分页结构、上传结构与全局契约一致。

## 证据记录

| 证据类型 | 路径或说明 | 验收人 | 结果 |
| --- | --- | --- | --- |
| 请求层契约 | `docs/api/phase-05-request-layer.md` | Codex | 通过 |
| 前端请求层 | `frontend/src/api/client.ts` | Codex | 通过 |
| 通用类型 | `frontend/src/types/api.ts` | Codex | 通过 |
| Mock path 清单 | `frontend/src/api/mockServerPaths.ts` | Codex | 通过 |
| 前端验收 | `docs/stage-check/stage-05-全端请求层与mock开关-frontend.md` | Codex | 通过 |

