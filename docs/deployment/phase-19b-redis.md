# Phase 19-B Redis 本地部署与一致性说明

Phase 19-B 在两个后端真实接入 Redis 7，用于可重建缓存和 20 秒短锁。MySQL 始终是订单、任务、报告、绑定、档案和审核数据的唯一事实来源。Redis 不保存登录态，不替代 JWT，也不作为业务写入成功的依据。

## 1. 配置

两个后端使用相同环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | Compose 内固定为 `redis` |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | 与 19-A Compose 配置保持一致 |

连接超时和命令超时默认均为 2 秒。自定义 `LettuceConnectionFactory` 会真实读取 `spring.data.redis.connect-timeout` 与 `spring.data.redis.timeout`，不会忽略配置。

使用 19-A 环境启动：

```powershell
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml ps
```

本地散进程诊断时，先启动 Redis，再分别启动两个后端：

```powershell
docker run -d --name carenest-redis-validation -p 16379:6379 redis:7.4-alpine
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='16379'
mvn -pl backend-user spring-boot:run
mvn -pl backend-care-admin spring-boot:run
```

该散进程方式只用于故障定位，正式阶段门禁仍以 19-A Compose 和 Nginx 统一入口为准。

## 2. 缓存键与失效矩阵

| 拥有者 | Key | TTL | 内容 | 提交后失效事件 | Redis 故障行为 |
| --- | --- | --- | --- | --- | --- |
| 护理/管理后端 | `carenest:service-items:on-shelf:v1` | 5 分钟 | 上架服务项目公开字段 | 服务项目新增、编辑、上下架、删除 | 直接查询 MySQL；写操作照常提交 |
| 用户后端 | `carenest:home:ELDER:{userHash}:v1` | 30 秒 | 当前长辈首页摘要 | 绑定、档案、地址、订单、报告变化 | 权限校验后查询 MySQL |
| 用户后端 | `carenest:home:FAMILY:{userHash}:v1` | 30 秒 | 当前家属首页摘要 | 绑定、scope、档案、地址、订单、报告变化 | 权限校验后查询 MySQL |
| 护理/管理后端 | `carenest:home:NURSE:{userHash}:v1` | 30 秒 | 当前护理员任务计数 | 派单、接单、任务状态、服务记录、报告变化 | 身份校验后查询 MySQL |
| 护理/管理后端 | `carenest:home:ADMIN:{userHash}:v1` | 30 秒 | 当前管理员业务计数 | 订单、任务、报告变化 | 角色校验后查询 MySQL |
| 护理/管理后端 | `carenest:home:CUSTOMER_SERVICE:{userHash}:v1` | 30 秒 | 当前客服业务计数 | 订单、任务、报告变化 | 角色校验后查询 MySQL |

`userHash` 为用户 ID 的 SHA-256 十六进制结果，key 中不出现原始用户 ID。同一事务涉及多个首页时，key 会去重并在提交后一次批量删除；回滚不删除缓存。

阶段 19 的完整档案保存和用药快捷新增在 MySQL 事务提交后失效对应长辈首页，以及该长辈全部 `ACTIVE` 家属绑定的首页缓存。健康档案正文包含敏感健康信息，不进入 Redis；读取始终执行权限校验并查询 MySQL。

首页缓存只包含计数、业务卡片和快捷入口，不包含手机号、JWT、密码、完整病历、完整报告或文件存储密钥。服务前健康摘要未在 19-B 缓存。

## 3. 短锁与数据库兜底

| 场景 | Key | TTL | 冲突结果 | MySQL 最终裁决 |
| --- | --- | --- | --- | --- |
| 派单、接单、改期、取消、任务/订单状态 | `carenest:lock:order:{orderId}` | 20 秒 | HTTP 409 | 状态和原预约时间条件更新；任务、服务记录唯一约束 |
| 报告确认、已有报告重新生成 | `carenest:lock:report:{reportId}` | 20 秒 | HTTP 409 | 报告和订单状态条件更新；报告确认唯一约束 |
| 后续档案归档 | `carenest:lock:archive:{taskId}` | 20 秒 | HTTP 409 | Phase 24 必须同时使用档案版本或行锁 |

锁使用随机所有权 token，并通过 Lua 比较 token 后删除。Spring 事务存在时，锁在事务完成后释放，不会在数据库提交前提前释放。Redis 不可用时锁返回 `UNAVAILABLE`，业务继续执行，但数据库条件更新、事务和唯一约束仍保证最多一个高风险写入成功。

已有数据库需由负责人确认无重复服务记录后显式执行：

```powershell
$env:MYSQL_PWD='<MYSQL_PASSWORD>'
Get-Content -Raw db/migration/phase-19b-service-record-concurrency-guard.sql | `
  mysql -h 127.0.0.1 -P 3306 -u <MYSQL_USER> <MYSQL_DATABASE>
```

迁移脚本发现同一订单存在多条 `care_service_record` 时会直接终止，不会自动删除业务数据。空库初始化已在 schema 基线中包含唯一约束。

## 4. 验证命令

```powershell
mvn -pl backend-user,backend-care-admin '-Dtest=RedisInfrastructureTest,CareAdminPhaseServiceCacheTest' test
docker exec carenest-redis-validation redis-cli --scan --pattern 'carenest:*'
docker exec carenest-redis-validation redis-cli TTL carenest:service-items:on-shelf:v1
```

故障回源验证：

```powershell
docker stop carenest-redis-validation
# 再次请求服务项目或首页，响应必须来自 MySQL，不能返回假成功。
docker start carenest-redis-validation
# 再次读取后应重建缓存。
```

停止诊断容器：

```powershell
docker rm -f carenest-redis-validation
```

## 5. 后续阶段约束

Phase 20-25 新增病历、审核和服务前摘要写操作时，必须同步扩展本文件的失效矩阵。Phase 24 实现正式归档接口时，必须使用已冻结的 archive key，并增加数据库版本校验。
