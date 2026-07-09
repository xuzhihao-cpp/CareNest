# CareNest 数据字典与状态字典

本文件是阶段 2 的唯一人工维护数据字典源。前端 mock、后端 DTO、数据库字段和接口文档必须优先引用本文件。

## 命名规则

| 对象 | 规则 | 示例 |
| --- | --- | --- |
| API 字段 | camelCase | `elderId`, `serverTime` |
| 数据库列 | snake_case | `elder_id`, `server_time` |
| 枚举值 | 大写英文 | `WAIT_DISPATCH`, `APPROVED` |
| 接口路径 | kebab-case + 资源名 | `/api/v1/service-items` |
| ID 字段 | 业务对象 + Id | `familyId`, `orderId` |

## 字段字典

| module | objectName | fieldName | dbColumn | zhName | type | required | dictCode | remark |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| common | ApiResponse | code | code | 响应码 | integer | yes |  | 0 表示成功，非 0 表示错误 |
| common | ApiResponse | message | message | 响应消息 | string | yes |  | 成功固定为 success，错误可展示 |
| common | ApiResponse | data | data | 响应数据 | object | yes |  | 具体结构由接口定义 |
| common | ApiResponse | traceId | trace_id | 请求追踪 ID | string | yes |  | 用于日志定位 |
| common | PageResult | records | records | 分页记录 | array | yes |  | 表格只读取 records |
| common | PageResult | total | total | 总数 | integer | yes |  | 总记录数 |
| common | PageResult | page | page | 当前页 | integer | yes |  | 从 1 开始 |
| common | PageResult | size | size | 每页条数 | integer | yes |  | 默认 10 |
| phase-01 | HealthResponse | status | status | 服务状态 | string | yes | healthStatus | 阶段 1 健康检查使用 |
| phase-01 | HealthResponse | appName | app_name | 应用名称 | string | yes |  | 固定为 CareNest |
| phase-01 | HealthResponse | version | version | 应用版本 | string | yes |  | 阶段 1 可使用 0.1.0 |
| phase-01 | HealthResponse | dbConnected | db_connected | 数据库连接状态 | boolean | yes |  | 阶段 1-2 可为 false |
| phase-01 | HealthResponse | serverTime | server_time | 服务端时间 | datetime | yes |  | ISO-8601，含 +08:00 |
| phase-01 | VersionResponse | gitCommit | git_commit | Git 提交号 | string | yes |  | 本地 mock 可用 local-kickoff |
| phase-01 | VersionResponse | buildTime | build_time | 构建时间 | datetime | yes |  | ISO-8601，含 +08:00 |
| phase-01 | VersionResponse | apiPrefix | api_prefix | API 前缀 | string | yes |  | 固定 `/api/v1` |
| phase-02 | DictionaryResponse | dictCode | dict_code | 字典编码 | string | yes | dictionaryCode | 枚举字典唯一编码 |
| phase-02 | DictionaryResponse | dictName | dict_name | 字典名称 | string | yes |  | 中文名称 |
| phase-02 | DictionaryResponse | items | items | 字典项列表 | array | yes |  | 元素结构为 DictItem |
| phase-02 | DictItem | value | value | 字典值 | string | yes |  | 枚举值或字段值 |
| phase-02 | DictItem | label | label | 展示名称 | string | yes |  | 前端展示中文 |
| phase-02 | DictItem | sort | sort | 排序 | integer | yes |  | 从 1 开始 |
| phase-02 | DictItem | enabled | enabled | 是否启用 | boolean | yes |  | false 表示暂不使用 |
| phase-02 | DictItem | remark | remark | 备注 | string | no |  | 说明业务含义 |
| identity | LoginRequest | username | username | 登录账号 | string | yes |  | 固定演示账号使用 |
| identity | LoginRequest | password | password | 登录密码 | string | yes |  | 仅请求入参，不落库明文 |
| identity | LoginResponse | token | token | 访问令牌 | string | yes |  | Authorization Bearer 使用 |
| identity | User | userId | user_id | 用户 ID | string | yes |  | 登录用户唯一 ID |
| identity | User | displayName | display_name | 展示名称 | string | yes |  | 前端顶部用户信息展示 |
| identity | User | roles | roles | 角色列表 | array | yes | roleCode | 登录用户角色集合 |
| identity | User | roleCode | role_code | 角色编码 | string | yes | roleCode | 角色和菜单权限使用 |
| identity | RoleMenu | menus | menus | 菜单列表 | array | yes |  | 当前用户可访问菜单 |
| identity | RoleMenu | name | name | 菜单名称 | string | yes |  | 菜单展示文本 |
| identity | RoleMenu | path | path | 菜单路径 | string | yes |  | uni-app 页面路径 |
| identity | RoleMenu | icon | icon | 菜单图标 | string | yes |  | 图标语义名称 |
| identity | RolePermission | roleId | role_id | 角色 ID | string | yes |  | 管理端保存角色权限使用 |
| identity | RolePermission | permissionCode | permission_code | 权限编码 | string | yes |  | 单个按钮或资源权限编码 |
| identity | RolePermissionRequest | permissionCodes | permission_codes | 权限编码集合 | array | yes |  | 保存角色权限入参 |
| identity | PermissionResponse | permissions | permissions | 权限列表 | array | yes |  | 当前登录角色权限集合 |
| identity | Permission | resourceType | resource_type | 资源类型 | string | yes |  | 页面、按钮或接口 |
| identity | Permission | resourceKey | resource_key | 资源标识 | string | yes |  | 资源唯一标识 |
| identity | Permission | action | action | 操作 | string | yes |  | view、create、update、delete 等 |
| phase-04 | HomeSummaryRequest | role | role | 请求角色 | string | yes | roleCode | 首页 summary 请求角色 |
| phase-04 | HomeSummaryRequest | currentUserId | current_user_id | 当前用户 ID | string | yes |  | 当前登录用户 ID |
| phase-04 | HomeSummaryResponse | cards | cards | 首页卡片 | array | yes |  | 四端首页关键指标卡片 |
| phase-04 | HomeSummaryResponse | quickActions | quick_actions | 快捷入口 | array | yes |  | 四端首页快捷动作 |
| phase-04 | HomeSummaryResponse | todoCount | todo_count | 待办数量 | integer | yes |  | 当前端首页待处理数量 |
| phase-04 | HomeCard | key | key | 唯一键 | string | yes |  | 前端渲染 key |
| phase-04 | HomeCard | unit | unit | 数值单位 | string | no |  | 卡片值单位 |
| phase-04 | HomeCard | trend | trend | 趋势说明 | string | no |  | 卡片辅助趋势文案 |
| phase-04 | HomeQuickAction | permissionCode | permission_code | 权限编码 | string | yes |  | 快捷入口按钮权限 |
| elder | ElderProfile | elderId | elder_id | 长辈 ID | string | yes |  | 长辈业务主键 |
| family | FamilyProfile | familyId | family_id | 家属 ID | string | yes |  | 家属业务主键 |
| nurse | NurseProfile | nurseId | nurse_id | 护理人员 ID | string | yes |  | 护理人员业务主键 |
| service | ServiceItem | serviceId | service_id | 服务项目 ID | string | yes |  | 服务项目业务主键 |
| order | NursingOrder | orderId | order_id | 订单 ID | string | yes |  | 护理订单业务主键 |
| order | NursingOrder | orderStatus | order_status | 订单状态 | string | yes | orderStatus | 预约、派单、服务和确认使用 |
| file | FileAsset | fileId | file_id | 文件 ID | string | yes |  | 上传文件业务主键 |
| file | FileAsset | url | url | 文件地址 | string | yes |  | 文件访问地址 |
| file | FileAsset | type | type | 文件类型 | string | yes |  | 图片、PDF、音频等 |
| file | FileAsset | auditStatus | audit_status | 审核状态 | string | yes | auditStatus | 文件、资料、资质审核使用 |
| binding | ElderFamilyBinding | bindingId | binding_id | 绑定 ID | string | yes |  | 长辈和家属绑定主键 |
| binding | ElderFamilyBinding | bindingStatus | binding_status | 绑定状态 | string | yes | bindingStatus | 绑定授权流程使用 |
| binding | ElderFamilyBinding | scopeCodes | scope_codes | 授权范围 | array | yes | bindingScope | 家属授权范围 |
| reminder | ReminderTask | reminderStatus | reminder_status | 提醒状态 | string | yes | reminderStatus | 提醒中心和执行记录使用 |
| metric | MetricRecord | metricStatus | metric_status | 指标状态 | string | yes | metricStatus | 护理指标校验使用 |
| ticket | AssistanceTicket | ticketStatus | ticket_status | 工单状态 | string | yes | ticketStatus | 人工协助与客服处理使用 |
| complaint | Complaint | complaintStatus | complaint_status | 投诉状态 | string | yes | complaintStatus | 投诉处理使用 |
| appeal | NurseAppeal | appealStatus | appeal_status | 申诉状态 | string | yes | appealStatus | 护理申诉使用 |
| article | TrainingArticle | articleStatus | article_status | 文章状态 | string | yes | articleStatus | 培训文章上下架使用 |
| identity | SysUser | username | username | 登录账号 | string | yes |  | 阶段 2 登录使用 |
| identity | SysUser | passwordHash | password_hash | 密码哈希 | string | yes |  | 后端不得明文保存密码 |
| identity | SysUser | displayName | display_name | 展示名称 | string | yes |  | 登录后显示 |
| identity | SysUser | phone | phone | 手机号 | string | no |  | 演示账号可填写 |
| identity | SysUser | accountStatus | account_status | 账号状态 | string | yes | accountStatus | 登录和禁用校验 |
| identity | SysRole | roleId | role_id | 角色 ID | string | yes |  | 系统角色主键 |
| identity | SysRole | roleName | role_name | 角色名称 | string | yes |  | 角色中文名称 |
| identity | LoginSession | sessionId | session_id | 会话 ID | string | yes |  | 登录会话主键 |
| identity | LoginSession | tokenHash | token_hash | Token 哈希 | string | yes |  | 不保存明文 token |
| identity | LoginSession | expireAt | expire_at | 过期时间 | datetime | yes |  | JWT 或会话过期 |
| identity | LoginSession | revokedAt | revoked_at | 撤销时间 | datetime | no |  | 退出登录使用 |
| permission | SysPermission | permissionId | permission_id | 权限 ID | string | yes |  | 权限主键 |
| permission | SysPermission | permissionCode | permission_code | 权限编码 | string | yes |  | 接口和按钮权限校验 |
| permission | SysPermission | permissionName | permission_name | 权限名称 | string | yes |  | 权限中文名称 |
| permission | SysPermission | permissionGroup | permission_group | 权限分组 | string | yes |  | 权限菜单分组 |
| common | OperationLog | logId | log_id | 操作日志 ID | string | yes |  | 操作日志主键 |
| common | OperationLog | operatorId | operator_id | 操作人 ID | string | no |  | 未登录可为空 |
| common | OperationLog | operationType | operation_type | 操作类型 | string | yes |  | 如 SEED_INIT、STATUS_CHANGE |
| common | OperationLog | bizType | biz_type | 业务类型 | string | yes |  | 业务对象类型 |
| common | OperationLog | bizId | biz_id | 业务 ID | string | no |  | 业务对象主键 |
| common | OperationLog | beforeValue | before_value | 变更前数据 | object | no |  | JSON 保存 |
| common | OperationLog | afterValue | after_value | 变更后数据 | object | no |  | JSON 保存 |
| common | BaseEntity | createdAt | created_at | 创建时间 | datetime | yes |  | 数据库通用审计字段 |
| common | BaseEntity | updatedAt | updated_at | 更新时间 | datetime | yes |  | 数据库通用审计字段 |

## 状态和枚举字典

| dictCode | dictName | value | label | sort | enabled | remark |
| --- | --- | --- | --- | --- | --- | --- |
| roleCode | 角色枚举 | ELDER | 长辈 | 1 | true | 长辈端用户 |
| roleCode | 角色枚举 | FAMILY | 家属 | 2 | true | 家属端用户 |
| roleCode | 角色枚举 | NURSE | 护理人员 | 3 | true | 护理端用户 |
| roleCode | 角色枚举 | ADMIN | 管理员 | 4 | true | 管理端管理员 |
| roleCode | 角色枚举 | CUSTOMER_SERVICE | 客服 | 5 | true | 客服与工单处理 |
| healthStatus | 健康检查状态 | UP | 正常 | 1 | true | 服务可用 |
| healthStatus | 健康检查状态 | DOWN | 异常 | 2 | true | 服务不可用 |
| dictionaryCode | 字典编码 | ALL | 全部核心字典 | 1 | true | 字典目录接口使用 |
| dictionaryCode | 字典编码 | roleCode | 角色枚举 | 2 | true | 角色和权限使用 |
| dictionaryCode | 字典编码 | orderStatus | 订单状态 | 3 | true | 预约与护理履约使用 |
| dictionaryCode | 字典编码 | auditStatus | 审核状态 | 4 | true | 文件、资料和资质审核使用 |
| dictionaryCode | 字典编码 | bindingStatus | 绑定状态 | 5 | true | 长辈家属绑定使用 |
| dictionaryCode | 字典编码 | reminderStatus | 提醒状态 | 6 | true | 提醒中心使用 |
| dictionaryCode | 字典编码 | metricStatus | 指标状态 | 7 | true | 护理指标校验使用 |
| dictionaryCode | 字典编码 | ticketStatus | 工单状态 | 8 | true | 人工协助和客服使用 |
| dictionaryCode | 字典编码 | complaintStatus | 投诉状态 | 9 | true | 投诉处理使用 |
| dictionaryCode | 字典编码 | appealStatus | 申诉状态 | 10 | true | 护理申诉使用 |
| dictionaryCode | 字典编码 | articleStatus | 文章状态 | 11 | true | 培训文章上下架使用 |
| dictionaryCode | 字典编码 | accountStatus | 账号状态 | 12 | true | 登录账号启停使用 |
| dictionaryCode | 字典编码 | bindingScope | 授权范围 | 13 | true | 长辈家属绑定授权范围 |
| accountStatus | 账号状态 | ENABLED | 启用 | 1 | true | 账号可登录 |
| accountStatus | 账号状态 | DISABLED | 禁用 | 2 | true | 账号不可登录 |
| accountStatus | 账号状态 | LOCKED | 锁定 | 3 | true | 安全策略锁定 |
| bindingScope | 授权范围 | HEALTH_VIEW | 查看健康档案 | 1 | true | 家属可查看健康档案 |
| bindingScope | 授权范围 | HEALTH_EDIT | 编辑健康档案 | 2 | true | 家属可编辑健康档案 |
| bindingScope | 授权范围 | ORDER_CREATE | 创建护理订单 | 3 | true | 家属可代长辈下单 |
| bindingScope | 授权范围 | REPORT_VIEW | 查看服务报告 | 4 | true | 家属可查看报告 |
| bindingScope | 授权范围 | REPORT_CONFIRM | 确认服务报告 | 5 | true | 家属可确认报告 |
| bindingScope | 授权范围 | ARCHIVE_EDIT | 编辑归档信息 | 6 | true | 家属可维护归档信息 |
| orderStatus | 订单状态 | WAIT_DISPATCH | 待派单 | 1 | true | 订单已提交，等待派单 |
| orderStatus | 订单状态 | DISPATCHED | 已派单 | 2 | true | 管理端已派给护理人员 |
| orderStatus | 订单状态 | ACCEPTED | 已接单 | 3 | true | 护理人员已接单 |
| orderStatus | 订单状态 | ON_THE_WAY | 前往中 | 4 | true | 护理人员正在前往服务地址 |
| orderStatus | 订单状态 | SERVING | 服务中 | 5 | true | 护理服务进行中 |
| orderStatus | 订单状态 | WAIT_REPORT | 待报告 | 6 | true | 服务结束，等待报告生成 |
| orderStatus | 订单状态 | WAIT_CONFIRM | 待确认 | 7 | true | 等待长辈或家属确认 |
| orderStatus | 订单状态 | COMPLETED | 已完成 | 8 | true | 订单闭环完成 |
| orderStatus | 订单状态 | CANCELED | 已取消 | 9 | true | 订单取消 |
| auditStatus | 审核状态 | PENDING | 待审核 | 1 | true | 上传后等待审核 |
| auditStatus | 审核状态 | APPROVED | 已通过 | 2 | true | 审核通过 |
| auditStatus | 审核状态 | REJECTED | 已驳回 | 3 | true | 审核驳回 |
| auditStatus | 审核状态 | NEED_MORE | 需补充 | 4 | true | 审核需要补充材料 |
| bindingStatus | 绑定状态 | PENDING | 待确认 | 1 | true | 家属发起绑定，等待长辈确认 |
| bindingStatus | 绑定状态 | ACTIVE | 已生效 | 2 | true | 绑定关系可用 |
| bindingStatus | 绑定状态 | REJECTED | 已拒绝 | 3 | true | 长辈拒绝绑定 |
| bindingStatus | 绑定状态 | REVOKED | 已撤销 | 4 | true | 家属或长辈撤销授权 |
| bindingStatus | 绑定状态 | EXPIRED | 已过期 | 5 | true | 邀请或授权过期 |
| reminderStatus | 提醒状态 | PENDING | 待执行 | 1 | true | 提醒尚未处理 |
| reminderStatus | 提醒状态 | DONE | 已完成 | 2 | true | 用户已完成提醒事项 |
| reminderStatus | 提醒状态 | SNOOZED | 稍后提醒 | 3 | true | 用户选择稍后提醒 |
| reminderStatus | 提醒状态 | NEED_HELP | 请求协助 | 4 | true | 用户需要人工协助 |
| reminderStatus | 提醒状态 | MISSED | 已错过 | 5 | true | 提醒时间已过且未处理 |
| metricStatus | 指标状态 | PENDING | 待提交 | 1 | true | 指标尚未提交 |
| metricStatus | 指标状态 | SUBMITTED | 已提交 | 2 | true | 指标已提交待校验 |
| metricStatus | 指标状态 | PASS | 已达标 | 3 | true | 指标满足要求 |
| metricStatus | 指标状态 | MISSING | 未完成 | 4 | true | 必填指标缺失 |
| metricStatus | 指标状态 | PENDING_PROOF | 待补证明 | 5 | true | 未完成原因需要证明 |
| metricStatus | 指标状态 | EXEMPT_APPROVED | 豁免通过 | 6 | true | 管理端同意豁免 |
| metricStatus | 指标状态 | EXEMPT_REJECTED | 豁免驳回 | 7 | true | 管理端驳回豁免 |
| ticketStatus | 工单状态 | PENDING | 待处理 | 1 | true | 工单新建 |
| ticketStatus | 工单状态 | PROCESSING | 处理中 | 2 | true | 客服或管理员处理中 |
| ticketStatus | 工单状态 | RESOLVED | 已解决 | 3 | true | 问题已解决 |
| ticketStatus | 工单状态 | CLOSED | 已关闭 | 4 | true | 工单关闭 |
| complaintStatus | 投诉状态 | PENDING | 待处理 | 1 | true | 投诉新建 |
| complaintStatus | 投诉状态 | PROCESSING | 处理中 | 2 | true | 管理端处理中 |
| complaintStatus | 投诉状态 | RESOLVED | 已解决 | 3 | true | 投诉已有处理结果 |
| complaintStatus | 投诉状态 | REJECTED | 已驳回 | 4 | true | 投诉不成立 |
| appealStatus | 申诉状态 | PENDING | 待处理 | 1 | true | 护理人员已提交申诉 |
| appealStatus | 申诉状态 | APPROVED | 申诉通过 | 2 | true | 管理端通过申诉 |
| appealStatus | 申诉状态 | REJECTED | 申诉驳回 | 3 | true | 管理端驳回申诉 |
| articleStatus | 文章状态 | DRAFT | 草稿 | 1 | true | 管理端编辑中 |
| articleStatus | 文章状态 | PUBLISHED | 已发布 | 2 | true | 护理端可见 |
| articleStatus | 文章状态 | OFFLINE | 已下线 | 3 | true | 护理端不可见 |
## 维护规则

- 字段进入两个以上模块前，必须先写入字段字典。
- 状态值一旦被接口、数据库或 mock 使用，必须先写入状态字典。
- 不允许同一含义出现多个字段名，例如 `list`、`items`、`records` 混用。
- 分页列表统一使用 `records`。
- 任何变更必须在 PR 中说明影响范围。
