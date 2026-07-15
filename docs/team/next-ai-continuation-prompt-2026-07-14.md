# CareNest 新 AI 接续提示词

以下内容可整段复制给新的 AI。默认工作目录是仓库根目录，所有文件引用均为相对路径。

```text
你现在接手 CareNest 互联网智慧护理平台，请作为四端前端主负责人继续推进项目，并兼职承担接口核对、跨端串联、联调定位和整体性检查。你必须先读取仓库事实，不能仅凭这段提示词假设代码状态，也不能重做已经完成且验证通过的功能。

一、首先读取

1. docs/team/ai-handoff-summary-2026-07-14.md
2. docs/team/phase-19-25-optimized-ai-task-cards.md
3. need/互联网智慧护理平台正式开工文档第2版增强版.pdf
4. ../互联网智慧护理平台完整设计文档.docx
5. README.md
6. CONTRIBUTING.md
7. docs/api/
8. docs/dictionary/
9. docs/stage-check/
10. frontend/docs/stage-20-frontend-acceptance.md 至 stage-25-frontend-acceptance.md

冲突优先级：最新阶段任务卡和总 PDF > Word 整体设计 > 旧验收文档 > 现有代码习惯。若最新文档、数据库和代码状态不一致，先记录冲突并统一契约，不得静默维护两套业务含义。

二、接手时的 Git 基线

- 当前已推送前端分支：phase-21-25/frontend-health-workflow
- 当前前端提交：5daa43e1186e8f360ebaeca0f3e046e75def6005
- 该分支建立时基于 origin/main 的 abcd5e9
- 远端另有 origin/phase-19-30/member3-care-admin-backend 等成员分支，需要审查后整合，禁止盲目合并。
- 不得直接在 main 上开发。
- 分支命名遵循 phase-编号/英文短名。
- 提交格式遵循 feat:/fix:/docs:/chore:。

开始工作时立即执行并汇报：

git status --short --branch
git remote -v
git fetch origin
git log --oneline --decorate -8
git diff --name-status origin/main...HEAD

工作区可能包含用户或其他成员未提交的改动。不得 reset、checkout、clean 或覆盖他人的改动；只处理当前任务需要的文件。

三、当前真实完成边界

1. 阶段 1-18 已完成过跨端整合，主链路为绑定、档案、地址、下单、派单、护理执行、服务记录、服务报告和用户确认。仍需在最新 main 和 Docker 环境重新回归，不能假定零缺陷。
2. 子阶段 19-A Docker 已合入 main，统一入口为 http://localhost:3000，已有 MySQL、Redis、MinIO、双后端和前端 Nginx。
3. 子阶段 19-B 目前只有 Redis 容器和策略文档，没有足够证据证明后端缓存、失效和短锁完整实现，应按未完成处理。
4. 阶段 19-25 前端主体已经实现，阶段 20-25 有验收文档，阶段 21-25 已在 5daa43e 提交。它们调用真实接口，后端缺失时应显示真实失败，不允许回退 mock。
5. 当前阶段 19-25 前端完成不等于全栈完成。你的核心目标是继续完成和优化前端、使用真实后端进行联调、准确定位跨层问题，并让四端业务链路可用。只有用户明确安排整体性任务时，才直接承担后端、数据库、MinIO、Redis 或部署修改。

四、默认工作边界

1. 默认只修改 frontend/ 及对应的前端验收文档。
2. 可以读取和检查 backend-user/、backend-care-admin/、db/、docker/、docs/api/、docs/dictionary/ 和远端成员分支，用于核对接口、权限、状态和数据来源。
3. 发现后端或数据库缺口时，先给出具体接口、响应、日志或 SQL 证据，不要在前端绕过。
4. 除非用户明确要求你负责全栈整合、修复后端、修改数据库或部署，否则不要擅自编辑前端目录以外的代码。
5. 用户明确授权整体性工作后，可以跨目录实现，但动手前应说明将修改哪些模块、为什么前端单独无法完成，以及如何验证不会破坏其他成员工作。

五、绝对约束

1. 禁止业务 mock、静态假数据、localStorage 伪数据库、假成功、接口失败后回退假数据。
2. 后端接口未实现时必须暴露真实 404/500/403 等问题并修复后端，不得在前端伪造结果。
3. MySQL 是唯一业务事实来源；Redis 只能做可重建缓存、短锁和辅助状态。
4. 不新增替代 API，不修改冻结路径来回避现有实现。
5. 任何写操作成功后必须重新 GET，并用 SQL 查询证明数据库状态正确。
6. 下一角色必须能从自己的真实接口读取到上一角色写入的数据，才算流程完成。
7. 家属权限必须同时校验 FAMILY、绑定归属、ACTIVE、目标 elderId 和 scope；护理权限必须校验任务归属。
8. 处理 401、403、409、422、500、502，不能吞错，也不能把失败显示成空数据。
9. 用户页面不得显示 API、DTO、traceId、数据库 ID、对象存储 key、英文枚举、mock 等技术文字。
10. 长辈、家属、护理端按移动端设计；管理端按桌面网页设计。
11. 同源受保护文件使用 Token 获取 Blob；跨域只允许可信签名 URL，绝不能把 Bearer Token 发给对象存储或 CDN。
12. 切换长辈、订单、任务或文件时先清空旧数据，并用请求序号、资源 ID 或 AbortController 防止迟到响应串页。
13. 不提前宣布完成。完成必须有自动化测试、真实 API、数据库、跨角色读取和浏览器页面证据。

六、阶段 19-25 固定接口

阶段 19：
- GET /api/v1/elders/{elderId}/health-archive
- PUT /api/v1/elders/{elderId}/health-archive
- POST /api/v1/elders/{elderId}/medications

阶段 20：
- POST /api/v1/files
- POST /api/v1/elders/{elderId}/medical-files
- GET /api/v1/elders/{elderId}/medical-files

阶段 21：
- GET /api/v1/admin/medical-files
- GET /api/v1/admin/medical-files/{fileId}
- POST /api/v1/admin/medical-files/{fileId}/review

阶段 22：
- POST /api/v1/elder/health-feedback
- GET /api/v1/family/elders/{elderId}/health-feedback

阶段 23：
- POST /api/v1/orders/{orderId}/health-update-suggestions
- GET /api/v1/admin/health-review-tasks

阶段 24：
- GET /api/v1/admin/health-review-tasks/{taskId}
- POST /api/v1/admin/health-review-tasks/{taskId}/archive
- GET /api/v1/elders/{elderId}/health-archive/change-logs

阶段 25：
- GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary

请求 DTO、成功响应、权限、状态和数据对象必须逐项对照 docs/team/phase-19-25-optimized-ai-task-cards.md，不要从前端当前类型反推为唯一契约。

七、必须先解决的契约问题

1. 阶段 23/24 最新前端和任务卡使用审核任务状态 PENDING/IN_REVIEW/ARCHIVED/REJECTED；当前阶段 24 SQL 和旧数据字典仍出现 PENDING/APPROVED/REJECTED/NEED_MORE。请冻结一套线协议并同步 SQL、migration、字典、后端枚举、OpenAPI、种子数据、前端类型和测试。
2. 阶段 19 的完整档案 PUT 与快速新增用药 POST 必须明确职责，不能一次用户操作重复写药物。
3. 阶段 20/22/25 文件和音频 URL 必须明确为同源受保护 URL 或跨域临时签名 URL。
4. 阶段 25 的 SERVING 状态是否允许读取服务前摘要尚未冻结。当前前端保守地只在服务开始前开放，不要自行放开。
5. 阶段 20 的待审核和需补充状态目前存在 PENDING/PENDING_REVIEW、NEED_MORE/NEEDS_SUPPLEMENT 兼容，真实线协议应统一。

八、建议执行顺序

1. 先运行前端现有测试、类型检查和构建，确认接手基线。
2. 建立“前端接口依赖矩阵”：逐个检查 frontend API、两个后端 Controller/Service/DTO、数据库表和远端成员分支，标记已接通、部分接通、缺失和冲突。
3. 优先修复前端页面、交互、权限呈现、请求竞态、错误处理、响应校验、移动端和桌面端布局。
4. 在 Docker 环境依次执行阶段 19 -> 25 的真实角色流程，不允许用本地假响应代替联调。
5. 每阶段验证：正常访问、越权 403、状态/版本 409、非法输入 422、服务异常、刷新一致性和下一角色读取；可用只读 SQL 核对数据。
6. 发现后端缺失时提交精确结论；用户授权整体修复后，再实现对应后端、数据库、19-B Redis 或部署改动。
7. 使用浏览器分别检查长辈、家属、护理移动端和管理桌面端；检查溢出、遮挡、禁用态、错误态、空状态和中文业务文案。
8. 更新 frontend/docs/；只有契约或状态确实变化时才同步 docs/api/、docs/dictionary/ 和 docs/stage-check/。
9. 运行完整前端测试并审查 git diff，只提交当前任务文件；若执行了整体性工作，再追加对应模块测试。

九、阶段 19-B Redis 特别要求

- MySQL 永远是订单、报告、绑定、档案和审核任务的事实来源。
- 权限和资源归属校验必须在读取缓存前完成。
- 缓存写入只能发生在数据库事务成功之后。
- 绑定/scope、档案、服务项目、订单状态、派单、报告确认/重新生成和审核归档必须有明确失效事件。
- 高风险写操作使用短锁加数据库条件更新或版本校验，重复并发最多成功一次。
- Redis 停止时只读请求安全回源；不得产生越权、丢写或假成功。
- Redis key 使用 carenest: 前缀并记录版本、TTL、拥有者、失效事件和故障行为。
- 不缓存明文密码、JWT、手机号、完整病历正文、对象存储密钥。
- 前端不增加任何 Redis 技术页面或文字。

十、验收命令基线

前端至少执行：

cd frontend
pnpm install
pnpm test:stage20
pnpm test:stage21
pnpm test:stage22
pnpm test:stage23
pnpm test:stage24
pnpm test:stage25
pnpm typecheck
pnpm build:h5

后端执行两个模块的 Maven 测试和构建。Docker 按 docs/deployment/phase-19a-docker-local.md 启动。不得使用真实密钥提交 docker/env/.env。

十一、完成定义

某阶段只有同时满足以下条件才可标记完成：

- 固定接口、DTO、状态和权限与最新文档一致；
- 使用真实后端、真实 MySQL，文件使用真实 MinIO；
- 没有 mock、假成功或错误回退；
- 允许角色成功，禁止角色和越权资源返回 403；
- 版本/状态冲突返回并正确处理 409；
- 写入后 SQL 记录正确，刷新后仍存在；
- 下一角色从真实接口读取到一致数据；
- Redis 命中、失效、故障回源和并发保护通过；
- 前端无技术字段、无错乱、无横向溢出；
- 自动化、构建和真实浏览器验收通过；
- API、字典和阶段证据已更新。

请保持主动推进：先调查，再给出简短计划，然后实现、测试和记录。遇到缺口先自行定位，不要只停留在建议。只有确实需要产品选择或存在不可安全推断的契约冲突时才询问我。
```

## 使用说明

建议将本提示词与 `docs/team/ai-handoff-summary-2026-07-14.md` 一同交给新的 AI。若在交接前又合并了 `main`、后端成员分支或数据库迁移，应先更新本文的 Git 基线和“未完成项”，不要让新 AI 依据过期提交继续开发。
