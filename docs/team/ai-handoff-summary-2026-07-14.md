# CareNest 项目 AI 交接总结

更新时间：2026-07-14（Asia/Shanghai）

## 1. 交接目标

本文用于让新的 AI 在不依赖历史对话的情况下，以四端前端开发为主要职责继续推进 CareNest 互联网智慧护理平台，并在需要时承担接口核对、跨端串联和整体性检查。文档区分以下三类事实：

1. 已提交并完成静态验证的代码；
2. 已建立但仍需真实后端、MySQL、MinIO、Redis 联调的功能；
3. 已知契约冲突和后续风险。

新的 AI 不应把“前端代码完成”理解为“全栈业务已经验收”，也不得用 mock、静态假数据、localStorage 伪数据、前端假成功或错误回退掩盖后端缺口。

新的 AI 默认只修改 `frontend/` 及对应前端验收文档。它可以读取和检查后端、数据库、Docker、接口文档与成员分支，以定位前端联调问题和形成全局结论；除非用户明确要求负责全栈整合或修改对应模块，否则不得擅自修改 `backend-user/`、`backend-care-admin/`、`db/` 或部署配置。

## 2. 当前 Git 基线

| 项目 | 当前值 |
| --- | --- |
| 远程仓库 | `https://github.com/xuzhihao-cpp/CareNest.git` |
| 当前开发分支 | `phase-21-25/frontend-health-workflow` |
| 当前代码提交 | `5daa43e1186e8f360ebaeca0f3e046e75def6005` |
| 提交说明 | `feat: complete phase 21-25 frontend health workflow` |
| 分支远端 | `origin/phase-21-25/frontend-health-workflow` |
| 建立分支时的 `origin/main` | `abcd5e9` |
| Pull Request 入口 | `https://github.com/xuzhihao-cpp/CareNest/pull/new/phase-21-25/frontend-health-workflow` |

生成本文前，代码分支与远端一致且工作区干净。本文和配套提示词是该提交之后新增的本地交接文档，除非后续另行提交，否则不属于 `5daa43e`。

不得直接在 `main` 上开发。开始任何工作前先执行 `git status --short --branch`、`git fetch origin` 和 `git log --oneline --decorate -5`，确认用户或其他成员是否已经更新分支。

## 3. 唯一指导资料与优先级

所有路径均以仓库根目录为默认工作目录。

1. `docs/team/phase-19-25-optimized-ai-task-cards.md`
   - 阶段 19-25、19-A Docker、19-B Redis 的当前详细任务卡与接口冻结约定。
   - 仓库中的 `need/phase-19-25-optimized-ai-task-cards (2).md` 是相近版本；两者少量差异中，`docs/team/` 版本补充了重复绑定 `409`、待确认授权和跨长辈 scope 等更严格规则，应作为团队执行版。
2. `need/互联网智慧护理平台正式开工文档第2版增强版.pdf`
   - 阶段 1-18、角色、权限、接口、验收和协作总规范。
3. `../互联网智慧护理平台完整设计文档.docx`
   - 产品整体设计与页面设计参考。
4. `README.md`、`CONTRIBUTING.md`
   - 仓库目录、分支、提交、Docker 和协作规则。
5. `docs/api/`、`docs/dictionary/`、`docs/stage-check/`、`frontend/docs/`
   - 接口、状态、数据库和阶段验收证据。

发生冲突时，优先采用最新阶段任务卡和总 PDF；Word 设计文档用于补充产品设计，不得覆盖冻结接口、状态、权限和验收规则。实际代码与文档不一致时，不得静默兼容成另一套业务，应记录差异并统一契约。

## 4. 系统结构与角色

| 模块 | 目录 | 主要职责 |
| --- | --- | --- |
| 四端前端 | `frontend/` | 长辈端、家属端、护理端移动页面和管理端桌面网页 |
| 用户侧后端 | `backend-user/` | 登录、绑定、档案、地址、家属订单、用户报告确认等 |
| 护理/管理后端 | `backend-care-admin/` | 服务项目、管理订单、派单、护理任务、服务记录、报告、审核工作流等 |
| 数据库 | `db/` | MySQL schema、migration、seed、状态和字段基线 |
| 容器与代理 | `docker/`、`docker-compose.yml`、`docker-compose.app.yml` | MySQL、Redis、MinIO、双后端、前端 Nginx |

固定角色包括 `ELDER`、`FAMILY`、`NURSE`、`ADMIN`、`CUSTOMER_SERVICE`。权限不能只检查角色；涉及家属资源时必须同时校验绑定属于当前家属、`bindingStatus === ACTIVE`、目标长辈一致以及相应 scope。涉及护理任务时必须校验任务确实派给当前护理人员。

## 5. 已有阶段状态

### 5.1 阶段 1-18

阶段 1-18 已在此前分支完成跨端整合，主链路包括：

`家属绑定长辈 -> 长辈确认 -> 档案/地址 -> 家属下单 -> 管理端派单 -> 护理端执行 -> 服务记录 -> 服务报告 -> 长辈/家属确认`

相关历史提交和总结：

- `c51c30d feat: integrate phase 06-18 real api workflows`
- `3c2412a feat: complete phase 01-18 integration checks`
- `docs/team/implementation-summary-2026-07-10-to-11.md`
- `docs/stage-check/phase-01-18-basic-integration-2026-07-11.md`

此前运行时前端已关闭 mock 回退，并修复了大量用户可见的 API、DTO、traceId、内部编号和英文枚举展示。但仓库仍可能保留阶段 1-18 的历史 mock 文件、测试辅助代码或兼容分支。后续若要求“源代码中完全不存在 mock”，需要另做全仓审计；不能仅凭全局 `USE_MOCK=false` 宣称彻底删除。

### 5.2 子阶段 19-A：Docker

Docker 全栈环境已合入 `main`，提交来源包括：

- `ce0ad0c feat: add phase 19 docker full-stack deployment`
- `9e59319 Merge pull request #20 from xuzhihao-cpp/phase-19/docker-full-stack`

已有文件与证据：

- `docker-compose.yml`
- `docker-compose.app.yml`
- `docker/backend-user.Dockerfile`
- `docker/backend-care-admin.Dockerfile`
- `docker/frontend.Dockerfile`
- `docker/nginx/default.conf`
- `docs/deployment/phase-19a-docker-local.md`
- `docs/stage-check/phase-19a-docker.md`

验收记录表明 MySQL、Redis、MinIO、双后端和前端 Nginx 共 6 个服务曾全部健康，四角色曾通过 `http://localhost:3000` 使用真实数据登录。重新验收前必须从 `docker/env/.env.example` 创建本机 `docker/env/.env`，替换所有 `change-me`，不得提交真实密钥。

### 5.3 子阶段 19-B：Redis

当前仓库已提供 Redis 容器和数据策略，但没有证据表明 19-B 后端缓存、失效矩阵和短锁已经完整实现。当前代码搜索未发现统一的 `RedisTemplate`、Redisson 或阶段 19-B 缓存服务实现。

因此 19-B 应视为“任务卡和基础设施已存在，业务实现待完成”。必须保持 MySQL 为唯一事实来源，Redis 只能用于可重建缓存、短锁和辅助状态；Redis 故障时读取应安全回源，高风险写入仍需数据库条件更新或版本校验保护。

## 6. 阶段 19-25 前端完成情况

### 阶段 19：健康档案增强

- 家属端提供慢病、当前用药、过敏、风险标签、照护计划五类分段编辑。
- 长辈端为简化只读摘要，阅读与编辑分离。
- 使用真实 `GET/PUT /api/v1/elders/{elderId}/health-archive` 契约。
- 保存携带档案版本，处理 `409` 并在成功后重新读取。
- 保存前核对展示具体变更，不只显示分类名。
- `POST /api/v1/elders/{elderId}/medications` 已定义为快速新增候选接口；当前完整档案编辑以一次 `PUT` 事务保存，后端联调前必须冻结两者职责，不能让同一次新增重复写入。

### 阶段 20：病历资料上传

- 两步真实流程：先 `POST /api/v1/files`，再 `POST /api/v1/elders/{elderId}/medical-files`。
- 上传成功、登记失败时保留 `uploadedFileId`，重试只重新登记，避免重复上传。
- 文件选择在上传完成后锁定，校验扩展名、MIME、文件头和大小，并校验成功响应必须有非空 `fileId`。
- 列表由真实 `GET /api/v1/elders/{elderId}/medical-files` 读取。
- 同源受保护预览使用 Bearer Token 获取 Blob；跨域仅允许签名 URL，不发送登录令牌。
- 前端兼容 `PENDING/PENDING_REVIEW` 和 `NEED_MORE/NEEDS_SUPPLEMENT`，但后端与数据库仍应统一为一套线协议。
- 验收记录：`frontend/docs/stage-20-frontend-acceptance.md`。

### 阶段 21：管理端病历审核

- 管理端桌面工作台包含真实筛选、分页、详情预览和审核区。
- 权限从真实 `GET /api/v1/auth/permissions` 读取，不再从 `/auth/me` 猜测。
- 支持管理员和客服角色加权限码，不因缺字段默认放行。
- 支持通过、驳回、要求补充；驳回和补充强制意见。
- 选择进入档案审核时必须至少有一项可提取且被明确选择。
- 审核后重新读取该记录最新详情，不把筛选后消失误当作详情刷新。
- 通过并提取时只创建后续审核任务，不直接写正式健康档案。

### 阶段 22：长辈健康反馈

- 长辈端支持疼痛、头晕、睡眠、饮食、精神状态等快捷反馈，以及文字和真实语音附件。
- 家属端仅在 `ACTIVE + HEALTH_VIEW` 下读取所选长辈的反馈时间线。
- 切换长辈使用请求序号与 elderId 双重校验，防止 A 长辈迟到响应覆盖 B 长辈。
- 同源音频带 Token 获取 Blob；跨域签名地址不得携带 Token，并限制协议。
- 严重反馈提供真实家属联系方式和平台协助页面；平台协助只展示现有可执行入口，不伪造已通知平台。

### 阶段 23：健康档案变更建议

- 护理端从真实当前订单、服务记录和服务报告上下文发起建议，不允许手工输入订单 ID、来源 ID 或字段代码。
- 建议提交后明确说明“等待审核，档案尚未修改”。
- 管理端/客服端通过真实审核任务列表读取待审建议。
- 健康建议状态和审核任务状态分离：
  - 建议：`PENDING/APPROVED/REJECTED/ARCHIVED`
  - 审核任务：`PENDING/IN_REVIEW/ARCHIVED/REJECTED`
- 服务记录和服务报告失败分别显示真实错误，不把 `403/500` 伪装成“暂无数据”。
- 建议原因前端限制为 255 字，与当前数据库 `VARCHAR(255)` 对齐。

### 阶段 24：健康信息审核归档

- 管理端为三栏桌面工作台：任务列表、来源/当前值/建议值对比、逐字段审核决定。
- 每个字段初始决定为空，审核员必须逐项选择，不默认全部采纳。
- 正式档案只能由 `POST /api/v1/admin/health-review-tasks/{taskId}/archive` 修改。
- 列表、详情、提交、筛选和切换均有请求竞态保护；列表读取失败时清空旧详情和提交能力。
- 处理 `409` 并重新读取数据库状态。
- 长辈/家属变更历史不显示审核员 ID、档案版本等技术字段。

### 阶段 25：服务前健康摘要

- 护理端从当前真实任务进入，只读调用 `GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary`。
- 当前仅在 `DISPATCHED`、`ACCEPTED`、`ON_THE_WAY` 等开始服务前状态显示入口；`SERVING` 是否允许尚未冻结，当前保守关闭。
- 固定展示顺序：重点风险、过敏、当前用药、慢病与照护要点、审核通过病历、近期服务摘要。
- 不显示订单 ID、长辈 ID、服务编码、文件 ID、存储路径、审核意见、档案版本或 traceId。
- 服务名称缺失时显示“上门护理服务”，不回退显示 `serviceId`。
- API 对集合元素做完整性校验，缺少 `timePoints` 等字段时返回“响应不完整”而不是页面崩溃。
- 摘要和病历预览均有切换/卸载取消机制，旧任务文件不会在切换后打开。

## 7. 阶段 19-25 固定接口速查

| 阶段 | 接口 |
| --- | --- |
| 19 | `GET /api/v1/elders/{elderId}/health-archive` |
| 19 | `PUT /api/v1/elders/{elderId}/health-archive` |
| 19 | `POST /api/v1/elders/{elderId}/medications` |
| 20 | `POST /api/v1/files` |
| 20 | `POST /api/v1/elders/{elderId}/medical-files` |
| 20 | `GET /api/v1/elders/{elderId}/medical-files` |
| 21 | `GET /api/v1/admin/medical-files` |
| 21 | `GET /api/v1/admin/medical-files/{fileId}` |
| 21 | `POST /api/v1/admin/medical-files/{fileId}/review` |
| 22 | `POST /api/v1/elder/health-feedback` |
| 22 | `GET /api/v1/family/elders/{elderId}/health-feedback` |
| 23 | `POST /api/v1/orders/{orderId}/health-update-suggestions` |
| 23 | `GET /api/v1/admin/health-review-tasks` |
| 24 | `GET /api/v1/admin/health-review-tasks/{taskId}` |
| 24 | `POST /api/v1/admin/health-review-tasks/{taskId}/archive` |
| 24 | `GET /api/v1/elders/{elderId}/health-archive/change-logs` |
| 25 | `GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary` |

请求和响应 DTO、分页结构、角色、scope、状态和数据库对象以 `docs/team/phase-19-25-optimized-ai-task-cards.md` 的全量接口表为准，不得另起替代路径。

## 8. 前端安全与交互不变量

1. 不使用 mock、假成功、静态业务数组、localStorage 伪数据库或失败后回退假数据。
2. 后端未实现时显示真实失败，不把错误改成空列表或成功提示。
3. 不向用户展示 API、DTO、traceId、数据库主键、英文枚举、对象存储 key 或内部技术状态。
4. 长辈、家属、护理端是移动端；管理端是桌面网页。
5. 所有用户可编辑日期、时间、电话、分数、枚举必须使用结构化控件和格式限制。
6. 用户切换长辈、订单、任务或文件时，先清空旧敏感数据，再使旧请求失效。
7. 同源受保护文件由前端携带 Token 获取 Blob；跨域必须是可信签名 URL，绝不能向第三方域名发送 Bearer Token。
8. 任何档案、订单、报告、审核写入成功后必须重新读取事实数据；页面本地状态不能代替数据库状态。
9. `403` 是真实权限结果，`409` 是真实状态/版本冲突，不得吞掉或自动假成功。

## 9. 已执行验证

本分支阶段 21-25 最近一次确认的前端验证结果：

- `pnpm test:stage21`：通过。
- `pnpm test:stage22`：通过。
- `pnpm test:stage23`：10 项通过。
- `pnpm test:stage24`：9 项通过。
- `pnpm test:stage25`：6 项通过。
- `pnpm typecheck`：通过。
- `pnpm build:h5`：通过。
- `git diff --check`：通过。
- 阶段 25 在 390x844 视口完成视觉检查，无横向溢出或内容遮挡。

阶段 20 的 `pnpm test:stage20` 在其验收记录中为 5 项通过。阶段 19 没有独立的 `test:stage19` 脚本，不应虚构其自动化覆盖。

阶段 21-25 验收文档位于：

- `frontend/docs/stage-21-frontend-acceptance.md`
- `frontend/docs/stage-22-frontend-acceptance.md`
- `frontend/docs/stage-23-frontend-acceptance.md`
- `frontend/docs/stage-24-frontend-acceptance.md`
- `frontend/docs/stage-25-frontend-acceptance.md`

上述测试大部分是前端规则、请求构造和页面静态验收。阶段 19-25 的真实后端接口、MySQL 写入、MinIO 文件和 Redis 一致性仍必须在完整 Docker 环境中验证。

## 10. 已知未完成与契约风险

### 10.1 后端实现与真实联调

阶段 19-25 前端已按文档建立真实 API 调用，但不代表当前 `main` 上两个后端均已实现全部接口。远端存在 `origin/phase-19-30/member3-care-admin-backend` 等成员分支，必须先审查 Controller、Service、DTO、权限和 SQL，再决定如何整合，不能盲目合并。

### 10.2 状态字典冲突

最新任务卡和阶段 23/24 前端将审核任务状态定义为：

`PENDING / IN_REVIEW / ARCHIVED / REJECTED`

当前数据库阶段 24 SQL 和数据库负责人记录仍出现：

`PENDING / APPROVED / REJECTED / NEED_MORE`

这是实际联调阻塞项。必须由全栈负责人冻结一套线协议，更新 SQL、数据字典、后端枚举、OpenAPI、前端类型、种子数据和测试，不允许长期依靠前端多枚举兼容掩盖冲突。

### 10.3 文件和音频授权

后端必须明确返回以下一种方式：

- 同源受保护路径，由前端带 Token 获取 Blob；
- 有时效的跨域签名 URL，由浏览器直接访问且不携带 Token。

不能返回普通跨域受保护 URL，也不能把永久对象存储地址暴露给客户端。

### 10.4 阶段 25 服务状态

`SERVING` 状态是否允许读取服务前摘要尚未冻结。当前前端只在开始服务前开放。后端和产品文档明确允许前，不得自行放开。

### 10.5 Redis 19-B

尚需完成统一 Redis 配置、缓存服务、短锁、写后失效、权限先于缓存、故障回源和敏感信息扫描。绑定/scope 变更、档案归档、服务项目上下架、订单状态、派单和报告确认都必须纳入失效或并发保护矩阵。

### 10.6 真实角色验收

至少需要使用五个真实角色和真实数据库执行：

- 正常访问；
- 资源不属于当前用户；
- 家属绑定失效；
- scope 缺失；
- 其他护理人员访问；
- 重复提交；
- 版本/状态冲突；
- Redis 停止后的安全行为；
- MinIO 上传、预览、过期；
- 页面刷新和角色切换后的数据一致性。

## 11. 建议的下一步顺序

1. 获取最新远端，但不要在未审查时直接把 `main` 或成员分支合入当前分支。
2. 以 `frontend/` 为默认工作范围，先运行现有测试并检查四端页面、权限态、错误态、空状态和移动/桌面布局。
3. 对比 `origin/main`、当前前端分支和各后端成员分支，列出阶段 19-25 前端依赖接口的已有、缺失和契约冲突。
4. 先推动团队冻结状态、DTO、权限、分页、文件 URL、阶段 19 用药写入和阶段 25 `SERVING` 边界；前端按冻结结果调整。
5. 使用 Docker 全栈环境按阶段 19 -> 25 顺序联调，验证每次前端操作确实写入 MySQL，并可由下一角色读取。
6. 修复真实联调暴露的前端问题，不为了迎合错误后端而新增平行 API 或 mock。
7. 若用户明确授权整体性工作，再修改对应后端、数据库、19-B Redis 或部署模块；修改前先说明跨目录原因和影响范围。
8. 更新对应前端验收文档；接口、字典或后端状态确有变化时，再同步更新全局文档。
9. 运行前端自动化、类型检查、构建和四端浏览器验收；承担全栈任务时再追加后端、数据库和 Docker 验证。
10. 按 `phase-编号/英文短名` 创建或使用阶段分支，提交和推送前再次检查 README 规则。

## 12. 交接底线

- 不回滚其他成员改动，不清理与任务无关的脏工作区。
- 不直接向 `main` 写业务提交。
- 不修改冻结接口来绕过实现困难。
- 不用 mock 通过验收。
- 不把前端显示成功当成数据库写入成功。
- 不把自动化测试通过当成真实权限、MinIO、Redis 和跨端流程已经通过。
- 完成声明必须附带命令、接口响应、数据库记录和下一角色可读取的证据。
