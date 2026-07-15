# 成员3阶段31验收记录

## 实现范围

- 护理本人读取服务前注意事项。
- 管理员/客服按 `CARE_ATTENTION_REVIEW` 只读审阅。
- 护理按 `NURSE_ATTENTION_ACK` 批量确认，重复确认幂等。
- 从已归档健康数据、服务项目和派单上下文生成最小必要快照；病历审核结果必须先归档。
- `SERVING` 状态流转前执行后端强制确认门禁。

未修改成员1数据库结构和种子，未实现成员4护理端或管理端页面，也未新增用户侧接口。

## 主要文件

| 文件 | 作用 |
| --- | --- |
| `backend-care-admin/src/main/java/com/csu/carenest/careadmin/attention/AttentionNoticeDtos.java` | 阶段31冻结 DTO |
| `backend-care-admin/src/main/java/com/csu/carenest/careadmin/attention/Phase31AttentionController.java` | GET/ACK 接口 |
| `backend-care-admin/src/main/java/com/csu/carenest/careadmin/attention/Phase31AttentionService.java` | 生成、权限、确认与门禁规则 |
| `backend-care-admin/src/main/java/com/csu/carenest/careadmin/attention/Phase31AttentionRepository.java` | 真实 MySQL 字段读写与幂等处理 |
| `backend-care-admin/src/main/java/com/csu/carenest/careadmin/phase/CareAdminPhaseService.java` | 开始服务状态机接入门禁 |
| `docs/api/phase-31-attention-notices-api.md` | 冻结接口与错误规则 |

## 自动化证据

执行：

```powershell
mvn -pl backend-care-admin "-Dtest=Phase31AttentionServiceTest,Phase31AttentionControllerTest,CareAdminPhaseServiceCacheTest" test
```

结果：19 项通过，0 失败，0 错误。

覆盖：

- GET/POST 冻结路径、统一响应字段和参数校验。
- 非关联护理返回 403。
- 管理审阅和护理确认必须具备冻结权限码。
- 重复 GET 不新增重复快照。
- 来源内容变化保留旧记录并创建新快照。
- 风险等级变化创建新快照并要求重新确认。
- 来源删除后旧记录失效，不再阻塞开始服务。
- 重复 ACK 不新增重复确认，首次确认时间不被覆盖。
- 订单取消后拒绝确认，确认表不产生新记录。
- 重新派单后旧护理确认不对新护理生效。
- 未确认必确认项时，后端拒绝进入 `SERVING` 且不更新任务状态。

执行成员3后端全量回归：

```powershell
mvn -pl backend-care-admin test
```

结果：55 项通过，0 失败，0 错误；阶段8至30既有后端测试保持通过。

仓库根目录执行 `mvn test`：`backend-user` 70 项、`backend-care-admin` 55 项，
合计 125 项通过，两个模块均为 `BUILD SUCCESS`。

## 待真实环境验证

本记录中的 SQL 业务测试使用 H2 MySQL 兼容模式。合并前仍需在完整 Docker 环境使用真实
MySQL 和五个演示账号验证接口响应、落库记录、取消订单及团队冻结的重新派单流程。
