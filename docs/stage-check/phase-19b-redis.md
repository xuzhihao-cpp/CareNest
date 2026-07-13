# Phase 19-B Redis 验收记录

验收日期：2026-07-13

分支：`feature/redis-phase-19b`

事实来源：本地 MySQL 8.0.44
Redis：独立 `redis:7.4-alpine` 容器，宿主机端口 `16379`

## 1. 自动化测试

已执行：

```powershell
mvn -pl backend-user,backend-care-admin '-Dtest=RedisInfrastructureTest,CareAdminPhaseServiceCacheTest' test
mvn -pl backend-user '-Dtest=Phase16ReportAckApiTest' test
mvn -pl backend-user,backend-care-admin test
```

结果：

- 两个后端 Redis 基础设施测试各 8 项通过，覆盖 JSON 缓存、故障回源、hashed user key、锁所有权 token、Redis 不可用、事务完成后释放锁和提交后批量失效。
- 护理/管理缓存与并发服务测试 5 项通过，覆盖 5 分钟服务项目缓存、管理查询绕过公开缓存、写后失效、锁冲突 409 和 Redis 不可用时的 MySQL 条件更新。
- 阶段 16 报告接口测试 5 项通过，覆盖长辈确认、家属驳回、归档建议、无 scope 403 和跨角色 403。
- 完整双后端测试通过：用户后端 36 项、护理/管理后端 13 项，共 49 项，0 failure、0 error。
- 同步修正了主分支遗留的测试契约漂移：满意度 OpenAPI 快照更新为 0-100，绑定测试隔离基础 ACTIVE 数据，scope 更新断言改为待长辈确认，地址断言改为当前业务格式。

## 2. 真实 Redis 与 MySQL 证据

两个后端使用真实 MySQL 启动在 `8081`、`8082`，Redis 使用独立容器，不使用 mock 回退。

### 服务项目缓存

首次和二次请求 `GET /api/v1/service-items` 均返回 2 条相同记录。Redis 生成：

```text
key = carenest:service-items:on-shelf:v1
ttl = 300
```

管理端对 `service_001` 执行一次真实同值更新后，接口返回业务成功，事务提交后该 key 从存在变为不存在。失败的 400 请求没有触发失效。

### 首页隔离与 TTL

长辈首页产生的 key：

```text
carenest:home:ELDER:120bf69bf3bba4e8cdb466be07c3bb81de7a819d54f3f164e25028d3e6c0d31d:v1
```

TTL 在 30 秒以内，key 不含原始用户 ID。长辈首页档案数量查询已限定当前登录用户，不读取其他长辈的数量。

### Redis 停机回源

停止独立 Redis 容器后再次请求服务项目，仍从 MySQL 返回相同 2 条记录；没有 mock 成功数据。恢复 Redis 后再次读取会重建 key。

观测结果：

```text
beforeCount = 2
afterRedisStopCount = 2
same = true
rebuilt = true
```

### 锁冲突

手工占用 `carenest:lock:report:report_001` 后，长辈确认同一报告返回 HTTP 409，报告和确认记录未被修改。锁释放使用 token 比较删除，事务存在时延迟到事务完成后释放。

另外创建可清理的专用测试数据并发送真实并发请求：

```text
同一订单并发派单 HTTP = [200, 409]
MySQL = DISPATCHED | 1 个任务 | 1 条状态日志

同一报告并发确认 HTTP = [200, 409]
MySQL = CONFIRMED | COMPLETED | 1 条确认记录
```

两组专用订单、任务、报告、确认和日志均在核验后删除，清理查询结果为 0。

### 敏感信息扫描

扫描 `carenest:*` 的 key/value，结果如下：

```text
hasPassword = false
hasBearerOrJwt = false
hasPhone = false
hasMinioSecret = false
hasMedicalText = false
```

## 3. 数据库并发保护

- `nursing_order` 状态更新使用 `order_id + 原状态` 条件更新。
- 改期使用 `order_id + 原状态 + 原预约时间` 条件更新。
- `nurse_task.order_id`、`service_report.order_id`、`care_report_ack.report_id` 使用唯一约束。
- Phase 19-B 为 `care_service_record.order_id` 增加唯一约束；本地执行前查询确认不存在重复订单记录。
- 唯一键冲突统一转换为既有 409，不向前端暴露数据库异常。

迁移已在本地 MySQL 8.0.44 实际执行。最终索引为 `uk_care_service_record_order`，重复订单查询结果为 0；迁移可重复执行。

## 4. 已知环境限制

以下 Compose 配置校验通过：

```powershell
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml config --quiet
```

完整 `up -d --build` 尚未取得成功证据。第一次执行时 Docker Desktop 在拉取 `maven:3.9.9-eclipse-temurin-17` 阶段长时间无进度；该镜像后来出现在本机，但 2026-07-13 再次执行完整命令约三分钟仍无任何输出或应用容器创建，`eclipse-temurin:17-jre-jammy`、`node:20-alpine` 和 `nginxinc/nginx-unprivileged:1.27-alpine` 也未拉取成功。挂起的 CLI 已终止，没有容器构建失败日志，也没有伪造全栈健康结果。该限制属于 Docker Hub/BuildKit 镜像拉取环境，不影响本次真实本地 MySQL + Redis 行为验证，但在进入后续阶段正式门禁前仍需在可拉取基础镜像的网络下重跑 19-A Compose、Nginx 四端和完整跨端链路。

## 5. 清理

验收结束后停止两个散进程后端并删除独立 `carenest-redis-validation` 容器。机器上其他 Redis/MinIO 容器不属于本次验收，不做停止或删除。
