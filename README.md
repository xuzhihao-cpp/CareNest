# CareNest

A senior medical care booking platform connecting elderly users, family members, caregivers, and administrators for home-based nursing services.

## 项目定位

CareNest 是一个互联网智慧护理平台，用于连接长辈、家属、护理人员、客服和平台管理员，优先跑通上门护理预约、护理执行、服务留档、报告确认和管理看板等核心链路。

当前仓库处于正式开发前的 GitHub 开工阶段。本阶段不实现业务代码，先冻结多人协作规则、目录职责、接口契约、数据字典和阶段验收方式。

## 技术方向

- 用户端与护理端：uni-app + Vue
- 后端：Spring Boot
- 数据库：关系型数据库，具体建表从阶段 3 开始
- 接口前缀：`/api/v1`
- 统一返回：`{code,message,data,traceId}`

## Docker 全栈联调

阶段 19-A 使用两层 Compose：`docker-compose.yml` 提供 MySQL、Redis、MinIO，`docker-compose.app.yml` 提供双后端和前端 Nginx。统一入口默认使用 `http://localhost:3000`；如果本机 3000 端口被系统保留或占用，只修改 `docker/env/.env` 中的 `FRONTEND_PORT`，例如本机验收使用 `http://localhost:5173`。

```powershell
Copy-Item docker/env/.env.example docker/env/.env
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml config
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml ps -a
```

启动前必须修改 `docker/env/.env` 中全部 `change-me` 值。完整说明见 `docs/deployment/phase-19a-docker-local.md`，验收证据见 `docs/stage-check/phase-19a-docker.md`。

## 开发泳道

| 泳道 | 主要职责 | 关键目录 |
| --- | --- | --- |
| 数据库 / 数据规范 | 数据字典、状态字典、表结构、初始化数据、ER 图 | `db/`, `docs/dictionary/data-dictionary.md` |
| 用户侧后端 | 登录、长辈端、家属端、绑定授权、健康档案、提醒、AI、人工协助 | `backend-user/`, `docs/api/` |
| 护理与管理后端 | 护理端、管理端、订单、服务项目、留档、审核、客服、评分、看板 | `backend-care-admin/`, `docs/api/` |
| 前端 | 长辈端、家属端、护理端、管理端页面、路由、表单、列表、mock | `frontend/`, `mock/` |

## 阶段 1-2 开工范围

### 阶段 1：项目目录与协作规范

- 建立固定目录结构。
- 明确 GitHub Issue、PR、CODEOWNERS 和阶段验收规则。
- 提供环境变量样例、变更日志和贡献规范。
- 验收证据记录到 `docs/stage-check/phase-01.md`。

### 阶段 2：数据字典与状态字典初版

- 建立 `docs/dictionary/data-dictionary.md` 作为唯一人工维护的数据字典源。
- 冻结字段命名、数据库列命名和核心枚举值。
- 冻结阶段 1-2 的接口契约和 mock JSON。
- 验收证据记录到 `docs/stage-check/phase-02.md`。

## 固定目录说明

| 目录 | 说明 |
| --- | --- |
| ackend-user/ | 用户侧后端工程目录，阶段 1-2 只保留目录说明。 |
| ackend-care-admin/ | 护理端与管理端后端工程目录，阶段 1-2 只保留目录说明。 |
| rontend/ | uni-app 前端工程目录，阶段 1-2 只保留目录说明。 |
| db/schema/ | 建表 SQL 目录，阶段 3 开始维护基础表结构。 |
| db/seed/ | 演示账号、演示数据和初始化数据目录。 |
| db/migration/ | 后续字段变更、表结构升级和数据修复脚本目录。 |
 main
| docs/api/ | 接口总契约和分阶段接口文档。 |
| docs/dictionary/ | 数据字典和状态字典目录，data-dictionary.md 是唯一人工维护源。 |
| docs/test/ | 测试用例、问题清单、联调记录和阶段测试报告目录。 |
| docs/stage-check/ | 每个阶段的验收证据和检查记录。 |
| docs/team/ | 成员分工、协作规则和会议记录。 |
| mock/ | 前端和接口联调前使用的 mock JSON。 |
| docker/ | 本地联调和部署配置目录，阶段 1-2 只保留目录说明。 |
| docker-compose.yml | 本地开发依赖编排文件，先提供 MySQL、Redis、MinIO。 |
## Git 协作规则

- 每个阶段必须先建 GitHub Issue。
- 每个阶段从 Issue 派生一个或多个 PR。
- 禁止直接向 `main` 提交业务改动。
- 分支命名使用 `phase-编号/英文短名`，例如：
  - `phase-01/project-structure`
  - `phase-02/data-dictionary`
  - `phase-01-02/github-kickoff`
- 提交信息示例：
  - `docs: add phase 01 kickoff structure`
  - `docs: add phase 02 data dictionary`
  - `feat:` 仅用于后续真实业务代码。

## 字段与接口冻结规则

- API 字段使用 `camelCase`。
- 数据库字段使用 `snake_case`。
- 枚举值使用大写英文。
- 任何被两个以上模块使用的字段，必须先进入 `docs/dictionary/data-dictionary.md`。
- 涉及接口路径、请求字段、响应字段、状态枚举的 PR，必须同步更新：
  - `docs/dictionary/data-dictionary.md`
  - `docs/api/`
  - `mock/`
  - 对应阶段验收记录

## 阶段验收规则

每个阶段完成后，必须在 `docs/stage-check/` 保存可复查证据，包括但不限于：

- 目录截图或文件树
- 接口请求与响应样例
- 数据库查询结果
- 前端页面截图
- mock JSON
- 联调说明

阶段验收先看可复查证据，再进入依赖它的下一阶段。
