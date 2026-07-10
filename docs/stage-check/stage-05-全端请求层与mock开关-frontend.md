# 阶段 5 验收记录：全端请求层与 mock 开关（前端）

## 阶段目标

- 封装统一 `request(method,url,data)`。
- 提供 `ApiResponse<T>`、`PageResult<T>`、`FileUploadResult`。
- 支持 `VITE_USE_MOCK` 和 `VITE_FRONTEND_API_BASE` 切换 mock/真实接口。

## 本次前端改动

- 新增 `frontend/src/api/client.ts`：
  - 统一读取 `VITE_USE_MOCK`。
  - 统一读取 `VITE_FRONTEND_API_BASE`。
  - 自动携带 `Authorization: Bearer <token>`。
  - 请求失败统一返回 `{code:500,message:"接口请求失败",data:{},traceId}`。
- 新增 `frontend/src/types/api.ts`：
  - `ApiResponse<T>`
  - `PageResult<T>`
  - `FileUploadResult`
- 新增 `frontend/src/api/mockServerPaths.ts`，登记阶段 1-4 当前 mock path。
- 阶段 1-4 API 文件已改为通过统一请求层调用真实接口。
- 新增 `frontend/.env.example`。
- 四端页面新增请求层状态面板，显示 `API_BASE`、`mode`、mock path 数量和通用类型。

## 测试结果

| 项目 | 结果 |
| --- | --- |
| `pnpm typecheck` | 通过 |
| `pnpm build:h5` 默认 mock 模式 | 通过 |
| mock 模式页面 | 显示 `mode mock`、`API_BASE /api/v1`、`mock paths 12` |
| `VITE_USE_MOCK=false` 构建 | 通过 |
| real 模式页面 | 显示 `mode real`、真实 `API_BASE` |
| real 模式首页字段 | 同样渲染 3 张卡片和 3 个快捷入口 |
| Console error | 无 |

## 截图证据

- `docs/stage-check/stage-05-request-layer-mock.png`
- `docs/stage-check/stage-05-request-layer-real.png`

## 说明

真实模式验收使用本地临时 API 服务 `http://127.0.0.1:4180/api/v1`，该服务返回与 `mock/` 相同 DTO，用于证明切换 `VITE_USE_MOCK=false` 后页面字段结构不变。

## 结论

阶段 5 前端 MVP 已完成。后续阶段新增接口时，只需要在业务 API 文件中调用统一 `request`，并同步 `mockServerPaths` 和对应 `mock/` JSON。

