# 阶段 06、09、16 旧业务回归修复记录

日期：2026-07-13

## 一、问题范围

本次修复针对 6 个与 Redis 无关的旧业务检查失败：

1. 家属处理服务报告归档建议返回 `403`。
2. 家属创建、长辈确认、家属更新授权及撤销绑定的流程测试因已有绑定返回 `409`。
3. 长辈读取并确认家属新建绑定的测试因已有绑定返回 `409`。
4. 家属不能通过长辈接口确认绑定的权限测试在前置创建阶段先返回 `409`。
5. 绑定范围更新测试仍按“家属更新后立即生效”的旧规则断言。
6. 服务地址测试仍断言旧版省市区编码拼接格式。

## 二、根因与修复

### 1. 阶段 16 归档建议权限

- 后端继续严格要求 `FAMILY + ACTIVE + REPORT_CONFIRM + ARCHIVE_EDIT`，没有放宽权限。
- 阶段 16 专项测试数据为 `binding_001` 补充 `ARCHIVE_EDIT`，使允许场景具备完整授权。
- 报告列表响应增加 `elderId`，结构冻结为 `{reportId,orderId,elderId,elderName}`。
- 家属端不再合并所有长辈的授权范围，而是按当前报告的 `elderId` 精确匹配 ACTIVE 绑定，再判断 `REPORT_CONFIRM` 和 `ARCHIVE_EDIT`。

### 2. 阶段 6 绑定测试隔离

- 保留“同一家属与长辈已有 `PENDING/ACTIVE` 绑定时创建返回 `409`”的业务规则。
- `UserSidePhaseApiTest` 使用测试事务，每条相关测试只在当前事务中清理 `family_demo + elder_001` 的种子绑定，测试结束自动回滚。
- 创建、确认、更新、撤销测试之间不再依赖执行顺序或长期累积数据。
- 家属更新 ACTIVE 绑定范围后，测试先验证 `scopeUpdatePending=true` 和 `pendingScopeCodes`；随后由长辈确认，确认后才断言新 `scopeCodes` 正式生效。
- “家属不能使用长辈确认接口”测试现在能够越过前置创建并真正执行 `403` 权限断言。

### 3. 阶段 9 地址格式

- `fullAddress` 正式格式统一为：`regionCode + 单个空格 + detailAddress`。
- 示例：`310101 人民路200号2单元301`。
- 后端实现、前端解析与测试断言现已一致，不再使用 `310000310100310101...` 旧拼接格式。

## 三、文档契约更新

最新版 `docs/team/phase-19-25-optimized-ai-task-cards.md` 已同步以下约定：

- 重复绑定返回 `409`，前端存在绑定时改用更新接口。
- ACTIVE 绑定范围变更必须经长辈再次确认，确认前原权限保持不变。
- 报告列表必须返回 `elderId`，前端必须逐报告匹配长辈绑定。
- `fullAddress` 使用区县编码、空格和详细地址组合。
- 集成测试必须事务回滚或使用独立测试数据，不得依赖演示脏数据。

## 四、主要修改文件

- `backend-user/src/main/java/com/csu/carenest/user/report/PendingReportResponse.java`
- `backend-user/src/main/java/com/csu/carenest/user/report/ReportAckService.java`
- `backend-user/src/test/java/com/csu/carenest/user/flow/UserSidePhaseApiTest.java`
- `backend-user/src/test/java/com/csu/carenest/user/flow/Phase16ReportAckApiTest.java`
- `backend-user/src/test/resources/phase16-test-data.sql`
- `frontend/src/types/stageSixteen.ts`
- `frontend/src/components/StageSixteenReportAckPanel.vue`
- `docs/team/phase-19-25-optimized-ai-task-cards.md`

## 五、验证结果

后端目标回归测试：

```bash
mvn -pl backend-user '-Dtest=UserSidePhaseApiTest,Phase16ReportAckApiTest' test
```

结果：共 13 项测试，`Failures: 0`、`Errors: 0`、`BUILD SUCCESS`。

前端验证：

```bash
pnpm typecheck
pnpm build:h5
```

结果：类型检查通过，H5 正式构建通过。

## 六、最终结论

原 6 个失败点已按当前业务规则修复。修复没有降低重复绑定和报告归档权限限制；测试数据、测试隔离、前后端资源归属判断及地址契约现已对齐。
