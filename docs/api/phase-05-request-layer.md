# 阶段 5 前端请求层契约

阶段 5 不新增业务接口路径，交付物为统一请求层、通用响应类型和 mock/真实接口切换规则。

## 统一请求函数

```ts
request<T>({
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: '/auth/me',
  data,
  mock
})
```

## 环境变量

```env
VITE_USE_MOCK=true
VITE_FRONTEND_API_BASE=/api/v1
```

- `VITE_USE_MOCK=true`：使用前端同结构 mock。
- `VITE_USE_MOCK=false`：调用 `VITE_FRONTEND_API_BASE + url`。
- 真实接口仍必须返回统一 `ApiResponse<T>`。

## 通用类型

```ts
interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

interface FileUploadResult {
  fileId: string;
  url: string;
  type: string;
  auditStatus: string;
}
```

## Mock Server Paths

Mock path 清单由 `frontend/src/api/mockServerPaths.ts` 维护，当前覆盖阶段 1-4 的接口路径。新增阶段接口时必须同步增加对应 mock path 记录。

