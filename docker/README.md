# docker

本目录用于后续维护本地联调和部署相关配置。

阶段 1-2 只保留目录说明，不提前编写 `docker-compose`，避免在数据库、后端端口、MinIO、Redis、Nginx 等依赖未最终确认前引入误导性配置。

后续建议文件：

- `docker-compose.dev.yml`：本地开发联调环境。
- `env/.env.example`：Docker 环境变量样例。
- `mysql/` 或 `postgres/`：数据库容器初始化配置。
- `minio/`：文件存储服务配置。
- `nginx/`：前后端反向代理配置。

规则：

- Docker 配置必须与 `.env.example`、`db/`、后端服务端口保持一致。
- 本地联调配置优先服务 MVP 核心链路，不提前加入复杂生产部署能力。