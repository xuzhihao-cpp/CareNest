# Docker deployment

CareNest 使用两层 Compose 文件：

- `docker-compose.yml`：MySQL、Redis、MinIO 与持久化数据卷。
- `docker-compose.app.yml`：用户后端、护理/管理后端、前端 H5/Nginx。

各成员维护边界：

| 角色 | 主责配置 |
| --- | --- |
| 成员 1 数据库与规范 | `docker-compose.yml`、`docker/mysql/`、Redis 认证、MinIO 业务桶初始化、环境变量和数据卷 |
| 成员 2 用户后端 | `docker/backend-user.Dockerfile`、8081 健康检查 |
| 成员 3 护理/管理后端 | `docker/backend-care-admin.Dockerfile`、8082 健康检查 |
| 成员 4 前端 | `docker/frontend.Dockerfile`、`docker/nginx/`、H5 生产构建 |
| 组长/联调 | `docker-compose.app.yml`、路由归属、全链路验收和发布说明 |

统一启动和验证方法见 `docs/deployment/phase-19a-docker-local.md`。成员不得单独维护另一份 Compose 或改变公共路由归属。
