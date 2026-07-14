# 阶段 19-20 全栈整合验收记录

验收日期：2026-07-14

验收分支：`phase-19-20/full-stack-integration`

本次验收范围：阶段 19 健康档案、阶段 20 病历资料上传/登记/列表/预览，覆盖前端、用户端后端、护理管理端后端、MySQL、Redis、MinIO 和前端 Nginx 统一入口。

## 本机环境

- Docker Compose 项目名：`carenest-run`
- 统一入口：`http://localhost:5173`
- 说明：仓库默认入口仍可使用 `FRONTEND_PORT=3000`；本机 Windows 保留了 3000 端口，因此 `docker/env/.env` 使用 `FRONTEND_PORT=5173`。
- MySQL：`localhost:33061`
- Redis：`localhost:36379`
- MinIO：`localhost:39000`，控制台 `localhost:39001`
- 用户后端：`127.0.0.1:18081`
- 护理/管理后端：`127.0.0.1:18082`

## 本次修复与整合点

- 阶段 19 后端补充风险标签白名单校验，非法风险标签返回 `422`，不写入健康档案。
- 阶段 20 后端补充扩展名、MIME 和文件头联合校验，防止改名伪装文件通过上传。
- 阶段 20 上传上限统一为业务 `20 MiB`；Nginx `client_max_body_size` 为 `21m`，仅预留 multipart 包装空间。
- 阶段 20 上传成功但登记失败时，前端保留已上传 `fileId`，后续只重新登记，不重复上传。
- 上传响应缺失有效 `fileId` 时，前端直接判定响应不完整，不再发起错误登记。
- MinIO 签名增加 `MINIO_REGION=us-east-1`，并区分容器内 endpoint 与浏览器 public endpoint。
- 种子病历文件改为真实 PDF 资产，MinIO 初始化时写入 `smart-nursing` 桶，数据库文件大小与对象大小一致。
- 真实整合脚本默认入口改为 `http://localhost:5173/api/v1`，也可通过 `CARENEST_API_BASE` 覆盖。

## 自动化验证

```powershell
pnpm test:stage20
pnpm typecheck
pnpm contract:check
pnpm build:h5
mvn -B -ntp -pl backend-user,backend-care-admin test
node docs/test/phase-19-20-full-stack-integration.mjs
```

结果：

- `pnpm test:stage20`：5 项通过。
- `pnpm typecheck`：通过。
- `pnpm contract:check`：通过。
- `pnpm build:h5`：通过。
- `mvn -B -ntp -pl backend-user,backend-care-admin test`：backend-user 61 项通过，backend-care-admin 13 项通过。
- `phase-19-20-full-stack-integration.mjs`：17 项真实全栈检查通过。

## 真实全栈检查覆盖

- 五类演示账号均通过真实数据库登录：长辈、家属、护理员、管理员、客服。
- 阶段 19 读取权限覆盖五角色，写入权限只允许具备有效绑定和编辑范围的家属。
- 阶段 19 保存五类健康档案，触发 Redis 首页缓存清理，版本冲突返回 `409`。
- 阶段 19 快速新增用药真实落库，重复项受控，变更日志和操作日志写入 MySQL。
- 阶段 20 拒绝未登录、伪装扩展名、MIME 不匹配、文件头异常和超限文件。
- 阶段 20 接受恰好 `20 MiB` 文件，超过 1 字节通过 Nginx 返回统一 `422`。
- 阶段 20 PDF、JPEG、PNG 均真实写入 MySQL 与 MinIO。
- 阶段 20 登记校验角色、ACTIVE 绑定、授权范围、资料类型和日期。
- 阶段 20 禁止一个用户登记另一个用户上传的文件资产。
- 阶段 20 列表与签名预览权限覆盖长辈、家属、护理员、管理员、客服。
- 阶段 20 `medical_file` 与 `file_asset` 状态一致，上传和登记审计日志完整。

## 浏览器抽查口径

- 长辈端：健康档案摘要显示真实档案内容，病历资料列表读取真实数据库记录，预览打开 MinIO 签名 PDF。
- 家属端：健康档案可编辑五类分段表单，病历资料显示两步上传/登记流程和真实病历列表。
- 护理端、管理端、客服端：阶段 19-20 用户侧功能入口不越权暴露；接口级权限由整合脚本覆盖 `403`。

## 已知边界

- 阶段 24 的健康档案变更记录后端尚未纳入本次阶段 20 整合，因此用户端出现“变更记录服务暂不可用”不计入阶段 19-20 缺陷。
- 历史阶段 6 前端仍保留 mock 分支，但真实模式通过 `isMockEnabled()` 不走 mock；阶段 19-20 本次验收不依赖 mock 数据。
- `docker/env/.env` 是本机私有文件，不提交；团队成员应从 `docker/env/.env.example` 复制并修改端口与密钥。
