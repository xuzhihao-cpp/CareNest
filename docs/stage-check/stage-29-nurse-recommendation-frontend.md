# 阶段29 护理推荐规则前端验收记录

## 验收依据

- 项目工作目录：`smart-nursing-platform/`
- 阶段任务卡：`docs/team/phase-26-31-optimized-ai-task-cards.md`
- 阶段范围：家属预约条件推荐、管理端订单推荐查看、中文业务展示、条件切换防串页。
- 前端不得计算推荐分、改写推荐原因、伪造技能字典或回退静态护理名单。

## 前端实现

- `frontend/src/components/StageTwentyNineRecommendationPanel.vue`
  - 预约条件完整后读取真实推荐接口。
  - 订单模式读取该订单已经形成的推荐结果。
  - 展示护理姓名、综合评分、中文技能名称、后端推荐原因和可预约状态。
  - 不展示护理内部编号，不在前端计算分数或拼接推荐原因。
  - 条件变化后立即清空旧结果，并阻止旧成功或旧错误响应写回新条件。
- `frontend/src/components/StageTenOrderPanel.vue`
  - 推荐区嵌入家属预约流程。
  - 快速切换长辈时清空旧地址，旧长辈地址响应不得覆盖当前长辈。
- `frontend/src/components/StageElevenAdminOrdersPanel.vue`
  - 推荐区嵌入管理端订单详情。
  - 快速切换订单时仅接受当前订单详情响应。
  - 已移除接口路径、原始响应、追踪编号和开发摘要。
  - `ACCEPTED`、`ON_THE_WAY` 等订单状态使用中文业务名称。
- `frontend/src/utils/latestRequestGate.ts`
  - 为地址、订单详情和推荐条件提供统一的最新请求判定。

## 接口契约

- `POST /api/v1/orders/recommend-nurses`
- `GET /api/v1/orders/{orderId}/recommendations`
- 推荐响应：`{nurses:[{nurseId,nurseName,score,matchedSkills,recommendReason,available}]}`
- 技能字典当前前端读取：`GET /api/v1/dictionaries/nurseServiceSkill`

前端使用 `nurseId` 作为请求关联字段和列表键，但不向用户显示该内部编号。

## 自动化验证

- `pnpm test:stage29`：8 项通过。
  - 精确校验预约推荐请求和上海时区格式。
  - 精确校验订单推荐读取路径。
  - 校验缺失字段、重复护理、非法评分和技术编码推荐原因。
  - 校验 401、403、404、409、422、500 不回退假数据。
  - 使用延迟 Promise 模拟长辈 A/B 地址请求乱序返回。
  - 使用延迟 Promise 模拟订单 A/B 详情乱序返回和旧错误晚到。
  - 模拟服务、地址、时间连续变化，验证仅最新推荐可以写回。
  - 检查家属端与管理端承载页面没有技术字段和静态推荐回退。
- `pnpm typecheck`：通过。
- `pnpm build:h5`：通过。
- `git diff --check`：通过。

## 当前真实联调阻塞

### 1. 推荐原因不符合冻结契约

后端当前仍可能返回“匹配技能：BASIC_CARE”一类包含技术编码的原因。阶段29要求后端直接生成中文业务原因，前端不得改写或伪造。因此这类响应会被前端判定为不完整。

需要后端返回类似“资质和培训有效，具备基础照护和生命体征观察能力，当前时段可预约”的业务说明，并保持推荐日志中的原因一致。

### 2. 技能字典接口尚未形成完整契约

前端依赖真实技能字典把 `matchedSkills` 转换为中文名称，但当前冻结接口表和后端路由尚未共同确认 `nurseServiceSkill` 字典读取接口。接口不可用时，页面会明确提示技能名称暂时无法读取，不会用硬编码或“其他技能”伪装。

## 结论

- 阶段29前端代码、中文展示、防串页和异常处理已通过本地自动化检查。
- 当前不能标记为全栈真实联调完成。
- 完成条件：后端返回纯中文推荐原因，并提供已冻结、可鉴权、可测试的护理技能字典接口；随后补真实角色、空候选、权限失败和跨端页面证据。
