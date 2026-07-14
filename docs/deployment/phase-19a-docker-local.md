# Phase 19-A Docker local deployment

本文所有路径均以项目根目录为起点。该环境用于本地和团队联调，不包含生产密钥管理、TLS 或多机编排。

## 1. 成员交付边界

| 成员 | 交付与验证 |
| --- | --- |
| 数据库与规范 | MySQL UTF-8、按阶段顺序初始化最新 schema/seed、已有数据库 migration、Redis/MinIO 数据卷、环境变量样例 |
| 用户后端 | Maven/JDK 17 多阶段镜像、非 root 运行、8081 数据库健康检查 |
| 护理/管理后端 | Maven/JDK 17 多阶段镜像、非 root 运行、8082 数据库健康检查 |
| 前端 | Node 20/pnpm 构建、非 root Nginx、SPA 回退、20 MB 上传代理、真实 `/api/v1` 请求 |
| 组长/联调 | Compose 合并、Nginx 路由矩阵、四端登录和跨端链路验收 |

## 2. 环境准备

1. 启动 Docker Desktop，并确认 `docker version` 可同时显示 Client 和 Server。
2. 将 `docker/env/.env.example` 复制为不提交的 `docker/env/.env`，修改所有 `change-me` 值。
3. 检查 3000、3306、6379、8081、8082、9000、9001 端口；冲突时只修改 `docker/env/.env` 中的宿主机端口。Windows 若保留 3000，可把 `FRONTEND_PORT` 改为 5173，并同步使用 `http://localhost:5173` 验收。
4. 容器内部固定使用服务名 `mysql`、`redis`、`minio`，禁止改为 `localhost`。

## 3. 启动

```powershell
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml config
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml ps
```

首次创建 MySQL 数据卷时，`docker/mysql/00-run-project-sql.sh` 按文件名顺序执行最新的 `db/schema/*.sql`，再执行 `db/seed/*.sql`。已确认的历史变更必须合入 schema 基线；`db/migration/` 挂载在 `/opt/carenest/migration`，只允许负责人针对已有数据库逐个确认并显式执行，禁止在空库初始化时批量重放。已有数据卷不会重复初始化，也不会被 Compose 自动删除。

`minio-init` 会在 MinIO 健康后创建 `MINIO_BUCKET`，成功退出后双后端才会启动。`REDIS_PASSWORD` 为空时用于本地无密码联调；设置后 Redis 与双后端会统一启用该密码。

## 4. 统一入口与路由

默认入口：

- 前端与统一 API：`http://localhost:3000`
- 本机端口冲突时以前端 `FRONTEND_PORT` 为准；例如 `FRONTEND_PORT=5173` 时入口为 `http://localhost:5173`
- 用户后端直连诊断：`http://127.0.0.1:8081/api/v1/health`
- 护理/管理后端直连诊断：`http://127.0.0.1:8082/api/v1/health`
- MinIO：`http://localhost:9000`，控制台 `http://localhost:9001`

Nginx 路由遵守现有前端代理顺序：

| 路由 | 服务 |
| --- | --- |
| `/api/v1/health`、`/version`、`/auth/**` | 用户后端 8081 |
| `/api/v1/elders/**`、`/service-addresses/**`、`/files/**` | 用户后端 8081 |
| `/api/v1/elder/{bindings,reports,home-summary,health-feedback}/**` | 用户后端 8081 |
| `/api/v1/family/{bindings,elders,reports,home-summary}/**` | 用户后端 8081 |
| `/api/v1/admin/roles/**` | 用户后端 8081 |
| 其他 `/api/v1/**`，包括订单、护理任务和管理业务 | 护理/管理后端 8082 |

Bearer Token、请求体、multipart 文件和后端 HTTP 状态均原样透传。业务页面不得直连容器服务名或写死 8081/8082。
阶段 20 病历资料业务上限为 20 MiB；Nginx `client_max_body_size` 设置为 21m，仅用于给 multipart 包装头预留空间，不能理解为允许上传 21 MiB 文件。
Nginx 通过 Docker 内置 DNS 动态解析两个后端服务名；后端成员单独重建容器后，不需要同步重启前端容器。

## 5. 验证

```powershell
Invoke-RestMethod http://localhost:3000/nginx-health
Invoke-RestMethod http://localhost:3000/api/v1/health
# 如果 FRONTEND_PORT=5173，则把上面两条的 3000 替换为 5173。
Invoke-RestMethod http://127.0.0.1:8082/api/v1/health
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml exec mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT COUNT(*) AS users FROM sys_user;"'
```

还必须使用真实演示账号验证四端登录、家属订单、管理派单、护理执行、服务报告和长辈/家属确认。只通过健康检查不等于业务验收通过。

## 6. 日志、停止和重置

```powershell
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml logs -f --tail 200
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml down
```

`down` 保留数据卷。只有明确需要清空全部本地演示数据并已备份时，才允许执行 `down -v`；该操作会永久删除本项目的 MySQL、Redis 和 MinIO 本地数据。

## 7. 安全边界

- 不提交 `docker/env/.env`、JWT 密钥或真实数据库/MinIO 密钥。
- 两个后端使用非 root 用户和只读根文件系统，临时文件只写入 `/tmp`。
- Redis 在 19-A 只作为基础服务；19-B 才接入缓存和短锁，不得把 Redis 当业务事实来源。
- `MINIO_ENDPOINT` 是容器内部地址，`MINIO_PUBLIC_ENDPOINT` 是浏览器可访问地址，二者不能混用。
