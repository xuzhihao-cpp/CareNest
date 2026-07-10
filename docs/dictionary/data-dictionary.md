# CareNest 鏁版嵁瀛楀吀涓庣姸鎬佸瓧鍏?

鏈枃浠舵槸闃舵 3 鐨勫敮涓€浜哄伐缁存姢鏁版嵁瀛楀吀婧愩€傚墠绔?mock銆佸悗绔?DTO銆佹暟鎹簱瀛楁鍜屾帴鍙ｆ枃妗ｅ繀椤讳紭鍏堝紩鐢ㄦ湰鏂囦欢銆?

## 鍛藉悕瑙勫垯

| 瀵硅薄 | 瑙勫垯 | 绀轰緥 |
| --- | --- | --- |
| API 瀛楁 | camelCase | `elderId`, `serverTime` |
| 鏁版嵁搴撳垪 | snake_case | `elder_id`, `server_time` |
| 鏋氫妇鍊?| 澶у啓鑻辨枃 | `WAIT_DISPATCH`, `APPROVED` |
| 鎺ュ彛璺緞 | kebab-case + 璧勬簮鍚?| `/api/v1/service-items` |
| ID 瀛楁 | 涓氬姟瀵硅薄 + Id | `familyId`, `orderId` |

## 瀛楁瀛楀吀

| module | objectName | fieldName | dbColumn | zhName | type | required | dictCode | remark |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| common | ApiResponse | code | code | 鍝嶅簲鐮?| integer | yes |  | 0 琛ㄧず鎴愬姛锛岄潪 0 琛ㄧず閿欒 |
| common | ApiResponse | message | message | 鍝嶅簲娑堟伅 | string | yes |  | 鎴愬姛鍥哄畾涓?success锛岄敊璇彲灞曠ず |
| common | ApiResponse | data | data | 鍝嶅簲鏁版嵁 | object | yes |  | 鍏蜂綋缁撴瀯鐢辨帴鍙ｅ畾涔?|
| common | ApiResponse | traceId | trace_id | 璇锋眰杩借釜 ID | string | yes |  | 鐢ㄤ簬鏃ュ織瀹氫綅 |
| common | PageResult | records | records | 鍒嗛〉璁板綍 | array | yes |  | 琛ㄦ牸鍙鍙?records |
| common | PageResult | total | total | 鎬绘暟 | integer | yes |  | 鎬昏褰曟暟 |
| common | PageResult | page | page | 褰撳墠椤?| integer | yes |  | 浠?1 寮€濮?|
| common | PageResult | size | size | 姣忛〉鏉℃暟 | integer | yes |  | 榛樿 10 |
| phase-01 | HealthResponse | status | status | 鏈嶅姟鐘舵€?| string | yes | healthStatus | 闃舵 1 鍋ュ悍妫€鏌ヤ娇鐢?|
| phase-01 | HealthResponse | appName | app_name | 搴旂敤鍚嶇О | string | yes |  | 鍥哄畾涓?CareNest |
| phase-01 | HealthResponse | version | version | 搴旂敤鐗堟湰 | string | yes |  | 闃舵 1 鍙娇鐢?0.1.0 |
| phase-01 | HealthResponse | dbConnected | db_connected | 鏁版嵁搴撹繛鎺ョ姸鎬?| boolean | yes |  | 闃舵 1-2 鍙负 false |
| phase-01 | HealthResponse | serverTime | server_time | 鏈嶅姟绔椂闂?| datetime | yes |  | ISO-8601锛屽惈 +08:00 |
| phase-01 | VersionResponse | gitCommit | git_commit | Git 鎻愪氦鍙?| string | yes |  | 鏈湴 mock 鍙敤 local-kickoff |
| phase-01 | VersionResponse | buildTime | build_time | 鏋勫缓鏃堕棿 | datetime | yes |  | ISO-8601锛屽惈 +08:00 |
| phase-01 | VersionResponse | apiPrefix | api_prefix | API 鍓嶇紑 | string | yes |  | 鍥哄畾 `/api/v1` |
| phase-06 | BindingRequest | elderInviteCode | elder_id | 闀胯緢閭€璇风爜 | string | yes |  | MVP 涓娇鐢?elder_id 浣滀负缁戝畾閭€璇风爜 |
| binding | ElderFamilyBinding | relationType | relation_type | 瀹跺睘鍏崇郴绫诲瀷 | string | no | relationType | 闀胯緢瀹跺睘缁戝畾鍏崇郴 |
| phase-07 | ElderProfileRequest | name | elder_name | 闀胯緢濮撳悕 | string | yes |  | 鎸?PDF 璇锋眰瀛楁鍛藉悕锛岃惤搴撳埌 elder_name |
| phase-07 | ElderProfileRequest | emergencyContacts |  | 绱ф€ヨ仈绯讳汉鍒楄〃 | array | yes |  | 鍏冪礌缁撴瀯涓?ElderContact |
| phase-07 | ElderProfileResponse | profileVersion | updated_at | 妗ｆ鐗堟湰 | string | yes |  | 鐢?elder_id 涓?updated_at 缁勫悎鐢熸垚 |
| phase-09 | ServiceAddressResponse | fullAddress |  | 瀹屾暣鏈嶅姟鍦板潃 | string | yes |  | 鐢辩渷甯傚尯缂栫爜鍜岃缁嗗湴鍧€鎷兼帴鐢熸垚 |
| phase-16 | ReportAckRequest | ackResult | ack_result | 纭缁撴灉 | string | yes | ackResult | 闀胯緢鎴栧灞炵‘璁ゆ姤鍛婄粨鏋?|
| phase-16 | ReportAckRequest | satisfaction | satisfaction | 婊℃剰搴?| integer | no |  | 鎶ュ憡纭鏃剁殑 1-5 鍒嗘弧鎰忓害 |
| phase-16 | ReportAckRequest | acceptedSuggestionIds | accepted_suggestion_ids | 鎺ュ彈鐨勫綊妗ｅ缓璁?ID 鍒楄〃 | array | no |  | 瀹跺睘澶勭悊妗ｆ鍙樻洿寤鸿浣跨敤 |
| phase-16 | ReportAckResponse | ackId | ack_id | 鎶ュ憡纭 ID | string | yes |  | 鎶ュ憡纭璁板綍涓婚敭 |
| phase-16 | ReportAckResponse | reportStatus | report_status | 鎶ュ憡鐘舵€?| string | yes | reportStatus | 纭鍚庢姤鍛婄姸鎬?|
| phase-18 | DemoDataStatusResponse | ready | ready | 婕旂ず鏁版嵁鏄惁灏辩华 | boolean | yes |  | 婕旂ず璐﹀彿涓庢牳蹇冨満鏅暟鎹潎瀛樺湪鏃朵负 true |
| phase-18 | DemoDataStatusResponse | accounts | accounts | 婕旂ず璐﹀彿鍒楄〃 | array | yes |  | 鍥哄畾婕旂ず璐﹀彿鐢ㄦ埛鍚嶉泦鍚?|
| phase-18 | DemoDataStatusResponse | scenarioCount | scenario_count | 婕旂ず鍦烘櫙鏁伴噺 | integer | yes |  | 褰撳墠宸插氨缁殑婕旂ず鍦烘櫙璁℃暟 |
| phase-02 | DictionaryResponse | dictCode | dict_code | 瀛楀吀缂栫爜 | string | yes | dictionaryCode | 鏋氫妇瀛楀吀鍞竴缂栫爜 |
| phase-02 | DictionaryResponse | dictName | dict_name | 瀛楀吀鍚嶇О | string | yes |  | 涓枃鍚嶇О |
| phase-02 | DictionaryResponse | items | items | 瀛楀吀椤瑰垪琛?| array | yes |  | 鍏冪礌缁撴瀯涓?DictItem |
| phase-02 | DictItem | value | value | 瀛楀吀鍊?| string | yes |  | 鏋氫妇鍊兼垨瀛楁鍊?|
| phase-02 | DictItem | label | label | 灞曠ず鍚嶇О | string | yes |  | 鍓嶇灞曠ず涓枃 |
| phase-02 | DictItem | sort | sort | 鎺掑簭 | integer | yes |  | 浠?1 寮€濮?|
| phase-02 | DictItem | enabled | enabled | 鏄惁鍚敤 | boolean | yes |  | false 琛ㄧず鏆備笉浣跨敤 |
| phase-02 | DictItem | remark | remark | 澶囨敞 | string | no |  | 璇存槑涓氬姟鍚箟 |
| identity | LoginRequest | username | username | 鐧诲綍璐﹀彿 | string | yes |  | 鍥哄畾婕旂ず璐﹀彿浣跨敤 |
| identity | LoginRequest | password | password | 鐧诲綍瀵嗙爜 | string | yes |  | 浠呰姹傚叆鍙傦紝涓嶈惤搴撴槑鏂?|
| identity | LoginResponse | token | token | 璁块棶浠ょ墝 | string | yes |  | Authorization Bearer 浣跨敤 |
| identity | User | userId | user_id | 鐢ㄦ埛 ID | string | yes |  | 鐧诲綍鐢ㄦ埛鍞竴 ID |
| identity | User | displayName | display_name | 灞曠ず鍚嶇О | string | yes |  | 鍓嶇椤堕儴鐢ㄦ埛淇℃伅灞曠ず |
| identity | User | roles | roles | 瑙掕壊鍒楄〃 | array | yes | roleCode | 鐧诲綍鐢ㄦ埛瑙掕壊闆嗗悎 |
| identity | User | roleCode | role_code | 瑙掕壊缂栫爜 | string | yes | roleCode | 瑙掕壊鍜岃彍鍗曟潈闄愪娇鐢?|
| identity | RoleMenu | menus | menus | 鑿滃崟鍒楄〃 | array | yes |  | 褰撳墠鐢ㄦ埛鍙闂彍鍗?|
| identity | RoleMenu | name | name | 鑿滃崟鍚嶇О | string | yes |  | 鑿滃崟灞曠ず鏂囨湰 |
| identity | RoleMenu | path | path | 鑿滃崟璺緞 | string | yes |  | uni-app 椤甸潰璺緞 |
| identity | RoleMenu | icon | icon | 鑿滃崟鍥炬爣 | string | yes |  | 鍥炬爣璇箟鍚嶇О |
| identity | RolePermission | roleId | role_id | 瑙掕壊 ID | string | yes |  | 绠＄悊绔繚瀛樿鑹叉潈闄愪娇鐢?|
| identity | RolePermission | permissionCode | permission_code | 鏉冮檺缂栫爜 | string | yes |  | 鍗曚釜鎸夐挳鎴栬祫婧愭潈闄愮紪鐮?|
| identity | RolePermissionRequest | permissionCodes | permission_codes | 鏉冮檺缂栫爜闆嗗悎 | array | yes |  | 淇濆瓨瑙掕壊鏉冮檺鍏ュ弬 |
| identity | PermissionResponse | permissions | permissions | 鏉冮檺鍒楄〃 | array | yes |  | 褰撳墠鐧诲綍瑙掕壊鏉冮檺闆嗗悎 |
| identity | Permission | resourceType | resource_type | 璧勬簮绫诲瀷 | string | yes |  | 椤甸潰銆佹寜閽垨鎺ュ彛 |
| identity | Permission | resourceKey | resource_key | 璧勬簮鏍囪瘑 | string | yes |  | 璧勬簮鍞竴鏍囪瘑 |
| identity | Permission | action | action | 鎿嶄綔 | string | yes |  | view銆乧reate銆乽pdate銆乨elete 绛?|
| phase-04 | HomeSummaryRequest | role | role | 璇锋眰瑙掕壊 | string | yes | roleCode | 棣栭〉 summary 璇锋眰瑙掕壊 |
| phase-04 | HomeSummaryRequest | currentUserId | current_user_id | 褰撳墠鐢ㄦ埛 ID | string | yes |  | 褰撳墠鐧诲綍鐢ㄦ埛 ID |
| phase-04 | HomeSummaryResponse | cards | cards | 棣栭〉鍗＄墖 | array | yes |  | 鍥涚棣栭〉鍏抽敭鎸囨爣鍗＄墖 |
| phase-04 | HomeSummaryResponse | quickActions | quick_actions | 蹇嵎鍏ュ彛 | array | yes |  | 鍥涚棣栭〉蹇嵎鍔ㄤ綔 |
| phase-04 | HomeSummaryResponse | todoCount | todo_count | 寰呭姙鏁伴噺 | integer | yes |  | 褰撳墠绔椤靛緟澶勭悊鏁伴噺 |
| phase-04 | HomeCard | key | key | 鍞竴閿?| string | yes |  | 鍓嶇娓叉煋 key |
| phase-04 | HomeCard | unit | unit | 鏁板€煎崟浣?| string | no |  | 鍗＄墖鍊煎崟浣?|
| phase-04 | HomeCard | trend | trend | 瓒嬪娍璇存槑 | string | no |  | 鍗＄墖杈呭姪瓒嬪娍鏂囨 |
| phase-04 | HomeQuickAction | permissionCode | permission_code | 鏉冮檺缂栫爜 | string | yes |  | 蹇嵎鍏ュ彛鎸夐挳鏉冮檺 |
| elder | ElderProfile | elderId | elder_id | 闀胯緢 ID | string | yes |  | 闀胯緢涓氬姟涓婚敭 |
| family | FamilyProfile | familyId | family_id | 瀹跺睘 ID | string | yes |  | 瀹跺睘涓氬姟涓婚敭 |
| nurse | NurseProfile | nurseId | nurse_id | 鎶ょ悊浜哄憳 ID | string | yes |  | 鎶ょ悊浜哄憳涓氬姟涓婚敭 |
| service | ServiceItem | serviceId | service_id | 鏈嶅姟椤圭洰 ID | string | yes |  | 鏈嶅姟椤圭洰涓氬姟涓婚敭 |
| order | NursingOrder | orderId | order_id | 璁㈠崟 ID | string | yes |  | 鎶ょ悊璁㈠崟涓氬姟涓婚敭 |
| order | NursingOrder | orderStatus | order_status | 璁㈠崟鐘舵€?| string | yes | orderStatus | 棰勭害銆佹淳鍗曘€佹湇鍔″拰纭浣跨敤 |
| file | FileAsset | fileId | file_id | 鏂囦欢 ID | string | yes |  | 涓婁紶鏂囦欢涓氬姟涓婚敭 |
| file | FileAsset | url | url | 鏂囦欢鍦板潃 | string | yes |  | 鏂囦欢璁块棶鍦板潃 |
| file | FileAsset | type | type | 鏂囦欢绫诲瀷 | string | yes |  | 鍥剧墖銆丳DF銆侀煶棰戠瓑 |
| file | FileAsset | auditStatus | audit_status | 瀹℃牳鐘舵€?| string | yes | auditStatus | 鏂囦欢銆佽祫鏂欍€佽祫璐ㄥ鏍镐娇鐢?|
| binding | ElderFamilyBinding | bindingId | binding_id | 缁戝畾 ID | string | yes |  | 闀胯緢鍜屽灞炵粦瀹氫富閿?|
| binding | ElderFamilyBinding | bindingStatus | binding_status | 缁戝畾鐘舵€?| string | yes | bindingStatus | 缁戝畾鎺堟潈娴佺▼浣跨敤 |
| binding | ElderFamilyBinding | scopeCodes | scope_codes | 鎺堟潈鑼冨洿 | array | yes | bindingScope | 瀹跺睘鎺堟潈鑼冨洿 |
| reminder | ReminderTask | reminderStatus | reminder_status | 鎻愰啋鐘舵€?| string | yes | reminderStatus | 鎻愰啋涓績鍜屾墽琛岃褰曚娇鐢?|
| metric | MetricRecord | metricStatus | metric_status | 鎸囨爣鐘舵€?| string | yes | metricStatus | 鎶ょ悊鎸囨爣鏍￠獙浣跨敤 |
| ticket | AssistanceTicket | ticketStatus | ticket_status | 宸ュ崟鐘舵€?| string | yes | ticketStatus | 浜哄伐鍗忓姪涓庡鏈嶅鐞嗕娇鐢?|
| complaint | Complaint | complaintStatus | complaint_status | 鎶曡瘔鐘舵€?| string | yes | complaintStatus | 鎶曡瘔澶勭悊浣跨敤 |
| appeal | NurseAppeal | appealStatus | appeal_status | 鐢宠瘔鐘舵€?| string | yes | appealStatus | 鎶ょ悊鐢宠瘔浣跨敤 |
| article | TrainingArticle | articleStatus | article_status | 鏂囩珷鐘舵€?| string | yes | articleStatus | 鍩硅鏂囩珷涓婁笅鏋朵娇鐢?|
| identity | SysUser | username | username | 鐧诲綍璐﹀彿 | string | yes |  | 闃舵 2 鐧诲綍浣跨敤 |
| identity | SysUser | passwordHash | password_hash | 瀵嗙爜鍝堝笇 | string | yes |  | 鍚庣涓嶅緱鏄庢枃淇濆瓨瀵嗙爜 |
| identity | SysUser | displayName | display_name | 灞曠ず鍚嶇О | string | yes |  | 鐧诲綍鍚庢樉绀?|
| identity | SysUser | phone | phone | 鎵嬫満鍙?| string | no |  | 婕旂ず璐﹀彿鍙～鍐?|
| identity | SysUser | accountStatus | account_status | 璐﹀彿鐘舵€?| string | yes | accountStatus | 鐧诲綍鍜岀鐢ㄦ牎楠?|
| identity | SysRole | roleId | role_id | 瑙掕壊 ID | string | yes |  | 绯荤粺瑙掕壊涓婚敭 |
| identity | SysRole | roleName | role_name | 瑙掕壊鍚嶇О | string | yes |  | 瑙掕壊涓枃鍚嶇О |
| identity | LoginSession | sessionId | session_id | 浼氳瘽 ID | string | yes |  | 鐧诲綍浼氳瘽涓婚敭 |
| identity | LoginSession | tokenHash | token_hash | Token 鍝堝笇 | string | yes |  | 涓嶄繚瀛樻槑鏂?token |
| identity | LoginSession | expireAt | expire_at | 杩囨湡鏃堕棿 | datetime | yes |  | JWT 鎴栦細璇濊繃鏈?|
| identity | LoginSession | revokedAt | revoked_at | 鎾ら攢鏃堕棿 | datetime | no |  | 閫€鍑虹櫥褰曚娇鐢?|
| permission | SysPermission | permissionId | permission_id | 鏉冮檺 ID | string | yes |  | 鏉冮檺涓婚敭 |
| permission | SysPermission | permissionCode | permission_code | 鏉冮檺缂栫爜 | string | yes |  | 鎺ュ彛鍜屾寜閽潈闄愭牎楠?|
| permission | SysPermission | permissionName | permission_name | 鏉冮檺鍚嶇О | string | yes |  | 鏉冮檺涓枃鍚嶇О |
| permission | SysPermission | permissionGroup | permission_group | 鏉冮檺鍒嗙粍 | string | yes |  | 鏉冮檺鑿滃崟鍒嗙粍 |
| common | OperationLog | logId | log_id | 鎿嶄綔鏃ュ織 ID | string | yes |  | 鎿嶄綔鏃ュ織涓婚敭 |
| common | OperationLog | operatorId | operator_id | 鎿嶄綔浜?ID | string | no |  | 鏈櫥褰曞彲涓虹┖ |
| common | OperationLog | operationType | operation_type | 鎿嶄綔绫诲瀷 | string | yes |  | 濡?SEED_INIT銆丼TATUS_CHANGE |
| common | OperationLog | bizType | biz_type | 涓氬姟绫诲瀷 | string | yes |  | 涓氬姟瀵硅薄绫诲瀷 |
| common | OperationLog | bizId | biz_id | 涓氬姟 ID | string | no |  | 涓氬姟瀵硅薄涓婚敭 |
| common | OperationLog | beforeValue | before_value | 鍙樻洿鍓嶆暟鎹?| object | no |  | JSON 淇濆瓨 |
| common | OperationLog | afterValue | after_value | 鍙樻洿鍚庢暟鎹?| object | no |  | JSON 淇濆瓨 |
| binding | AuthorizationScope | scopeCode | scope_code | 鎺堟潈鑼冨洿缂栫爜 | string | yes | bindingScope | 瀹跺睘鎺堟潈鑼冨洿缂栫爜 |
| binding | AuthorizationScope | scopeName | scope_name | 鎺堟潈鑼冨洿鍚嶇О | string | yes |  | 鎺堟潈鑼冨洿涓枃鍚嶇О |
| elder | ElderProfile | elderName | elder_name | 闀胯緢濮撳悕 | string | yes |  | 闀胯緢鍩虹妗ｆ |
| elder | ElderProfile | gender | gender | 鎬у埆 | string | no | gender | 闀胯緢鍩虹妗ｆ |
| elder | ElderProfile | birthDate | birth_date | 鍑虹敓鏃ユ湡 | date | no |  | 鐢ㄤ簬璁＄畻骞撮緞 |
| elder | ElderProfile | careLevel | care_level | 鐓ф姢绛夌骇 | string | no | careLevel | 闀胯緢鍩虹妗ｆ |
| elder | ElderProfile | emergencyContactName | emergency_contact_name | 绱ф€ヨ仈绯讳汉濮撳悕 | string | no |  | 闀胯緢鍩虹妗ｆ |
| elder | ElderProfile | emergencyContactPhone | emergency_contact_phone | 绱ф€ヨ仈绯讳汉鐢佃瘽 | string | no |  | 闀胯緢鍩虹妗ｆ |
| elder | ElderProfile | healthSummary | health_summary | 鍋ュ悍鎽樿 | string | no |  | 鎶ょ悊鍓嶆憳瑕?|
| elder | ElderContact | contactId | contact_id | 鑱旂郴浜?ID | string | yes |  | 鑱旂郴浜轰富閿?|
| elder | ElderContact | contactName | contact_name | 鑱旂郴浜哄鍚?| string | yes |  | 鑱旂郴浜轰俊鎭?|
| elder | ElderContact | contactPhone | contact_phone | 鑱旂郴浜虹數璇?| string | yes |  | 鑱旂郴浜轰俊鎭?|
| elder | ElderContact | relationType | relation_type | 鍏崇郴绫诲瀷 | string | no | relationType | 涓庨暱杈堝叧绯?|
| elder | HealthArchiveChangeLog | changeLogId | change_log_id | 鍋ュ悍妗ｆ鍙樻洿鏃ュ織 ID | string | yes |  | 妗ｆ鍙樻洿鐣欑棔 |
| elder | HealthArchiveChangeLog | changeType | change_type | 鍙樻洿绫诲瀷 | string | yes |  | 妗ｆ鍙樻洿绫诲瀷 |
| service | ServiceItem | serviceName | service_name | 鏈嶅姟椤圭洰鍚嶇О | string | yes |  | 绠＄悊绔淮鎶?|
| service | ServiceItem | serviceDesc | service_desc | 鏈嶅姟璇存槑 | string | no |  | 瀹跺睘绔睍绀?|
| service | ServiceItem | priceCent | price_cent | 浠锋牸鍒?| integer | yes |  | 閲戦缁熶竴鐢ㄥ垎 |
| service | ServiceItem | durationMinutes | duration_minutes | 鏈嶅姟鏃堕暱鍒嗛挓 | integer | yes |  | 鏈嶅姟椤圭洰鏃堕暱 |
| service | ServiceItem | serviceStatus | service_status | 鏈嶅姟鐘舵€?| string | yes | serviceStatus | 涓婁笅鏋朵娇鐢?|
| address | ServiceAddress | addressId | address_id | 鏈嶅姟鍦板潃 ID | string | yes |  | 鏈嶅姟鍦板潃涓婚敭 |
| address | ServiceAddress | provinceCode | province_code | 鐪佷唤缂栫爜 | string | yes |  | 琛屾斂鍖哄垝 |
| address | ServiceAddress | cityCode | city_code | 鍩庡競缂栫爜 | string | yes |  | 琛屾斂鍖哄垝 |
| address | ServiceAddress | regionCode | region_code | 鍖哄幙缂栫爜 | string | yes |  | 琛屾斂鍖哄垝 |
| address | ServiceAddress | detailAddress | detail_address | 璇︾粏鍦板潃 | string | yes |  | 涓婇棬鏈嶅姟鍦板潃 |
| address | ServiceAddress | isDefault | is_default | 鏄惁榛樿鍦板潃 | boolean | yes |  | 棰勭害榛樿閫夋嫨 |
| order | NursingOrder | addressId | address_id | 鏈嶅姟鍦板潃 ID | string | yes |  | 闃舵 10 涓嬪崟浣跨敤 |
| order | NursingOrder | scheduledStartAt | scheduled_start_at | 棰勭害寮€濮嬫椂闂?| datetime | yes |  | 闃舵 10 涓嬪崟浣跨敤 |
| order | NursingOrder | scheduledEndAt | scheduled_end_at | 棰勭害缁撴潫鏃堕棿 | datetime | no |  | 鐢辨湇鍔℃椂闀挎帹瀵兼垨鍓嶇閫夋嫨 |
| order | NursingOrder | servicePriceCent | service_price_cent | 鏈嶅姟浠锋牸鍒?| integer | yes |  | 涓嬪崟鏃跺揩鐓т环鏍?|
| order | NursingOrder | contactName | contact_name | 鑱旂郴浜哄鍚?| string | yes |  | 涓婇棬鑱旂郴淇℃伅 |
| order | NursingOrder | contactPhone | contact_phone | 鑱旂郴浜虹數璇?| string | yes |  | 涓婇棬鑱旂郴淇℃伅 |
| order | NursingOrder | remark | remark | 澶囨敞 | string | no |  | 涓嬪崟澶囨敞 |
| order | NursingOrder | createdBy | created_by | 鍒涘缓浜虹敤鎴?ID | string | no |  | 涓嬪崟鎿嶄綔浜?|
| order | OrderStatusLog | statusLogId | status_log_id | 璁㈠崟鐘舵€佹棩蹇?ID | string | yes |  | 鐘舵€佹棩蹇椾富閿?|
| order | OrderStatusLog | fromStatus | from_status | 鍙樻洿鍓嶇姸鎬?| string | no | orderStatus | 鍒濆鍒涘缓鍙负绌?|
| order | OrderStatusLog | toStatus | to_status | 鍙樻洿鍚庣姸鎬?| string | yes | orderStatus | 鐩爣璁㈠崟鐘舵€?|
| order | OrderStatusLog | changedBy | changed_by | 鍙樻洿浜虹敤鎴?ID | string | no |  | 鐘舵€佸彉鏇存搷浣滀汉 |
| order | OrderStatusLog | changeReason | change_reason | 鍙樻洿鍘熷洜 | string | no |  | 鐘舵€佸彉鏇磋鏄?|
| task | NurseTask | taskId | task_id | 鎶ょ悊浠诲姟 ID | string | yes |  | 娲惧崟鍚庣敓鎴愮殑鎶ょ悊浠诲姟 |
| task | NurseTask | orderId | order_id | 璁㈠崟 ID | string | yes |  | 鍏宠仈鎶ょ悊璁㈠崟 |
| task | NurseTask | nurseId | nurse_id | 鎶ょ悊浜哄憳鐢ㄦ埛 ID | string | yes |  | 琚淳鍗曠殑鎶ょ悊浜哄憳 |
| task | NurseTask | taskStatus | task_status | 浠诲姟鐘舵€?| string | yes | taskStatus | 鎶ょ悊绔换鍔″伐浣滃彴浣跨敤 |
| task | NurseTask | dispatchRemark | dispatch_remark | 娲惧崟澶囨敞 | string | no |  | 绠＄悊绔淳鍗曡鏄?|
| record | CareServiceRecord | recordId | record_id | 鏈嶅姟璁板綍 ID | string | yes |  | 鎶ょ悊鏈嶅姟璁板綍涓婚敭 |
| record | CareServiceRecord | startTime | start_time | 鏈嶅姟寮€濮嬫椂闂?| datetime | yes |  | 鎶ょ悊鏈嶅姟寮€濮嬫椂闂?|
| record | CareServiceRecord | endTime | end_time | 鏈嶅姟缁撴潫鏃堕棿 | datetime | no |  | 鎶ょ悊鏈嶅姟缁撴潫鏃堕棿 |
| record | CareServiceRecord | content | content | 鏈嶅姟鍐呭 | string | yes |  | 鎶ょ悊鏈嶅姟璁板綍姝ｆ枃 |
| record | CareServiceRecord | nursingAdvice | nursing_advice | 鎶ょ悊寤鸿 | string | no |  | 鏈嶅姟鍚庡缓璁?|
| record | CareServiceRecord | abnormalFlag | abnormal_flag | 鏄惁寮傚父 | boolean | yes |  | 寮傚父鏈嶅姟鏍囪 |
| record | VitalSignRecord | vitalId | vital_id | 鐢熷懡浣撳緛璁板綍 ID | string | yes |  | 鐢熷懡浣撳緛璁板綍涓婚敭 |
| record | VitalSignRecord | measuredAt | measured_at | 娴嬮噺鏃堕棿 | datetime | yes |  | 鐢熷懡浣撳緛娴嬮噺鏃堕棿 |
| record | VitalSignRecord | temperature | temperature | 浣撴俯 | decimal | no |  | 鎽勬皬搴?|
| record | VitalSignRecord | pulse | pulse | 鑴夋悘 | integer | no |  | 娆?鍒嗛挓 |
| record | VitalSignRecord | bloodOxygen | blood_oxygen | 琛€姘?| integer | no |  | 鐧惧垎姣?|
| report | ServiceReport | reportId | report_id | 鏈嶅姟鎶ュ憡 ID | string | yes |  | 鏈嶅姟鎶ュ憡涓婚敭 |
| report | ServiceReport | reportStatus | report_status | 鎶ュ憡鐘舵€?| string | yes | reportStatus | 鏈嶅姟鎶ュ憡鐢熸垚鍜岀‘璁ゆ祦绋?|
| report | ServiceReport | summary | summary | 鎶ュ憡鎽樿 | string | yes |  | 鏈嶅姟鎶ュ憡鎽樿 |
| report | ServiceReport | generatedBy | generated_by | 鐢熸垚浜虹敤鎴?ID | string | no |  | 鎶ょ悊浜哄憳鎴栫郴缁熺敓鎴?|
| report | ServiceReportItem | itemId | item_id | 鎶ュ憡鏄庣粏 ID | string | yes |  | 鏈嶅姟鎶ュ憡鏄庣粏涓婚敭 |
| report | ServiceReportItem | itemType | item_type | 鏄庣粏绫诲瀷 | string | yes | reportItemType | 鏈嶅姟璁板綍銆佺敓鍛戒綋寰佹垨鎶ょ悊寤鸿 |
| report | CareReportAck | ackId | ack_id | 鎶ュ憡纭 ID | string | yes |  | 鎶ュ憡纭璁板綍涓婚敭 |
| report | CareReportAck | ackRole | ack_role | 纭浜鸿鑹?| string | yes | roleCode | 闀胯緢鎴栧灞炵‘璁?|
| report | CareReportAck | ackResult | ack_result | 纭缁撴灉 | string | yes | ackResult | 鎺ュ彈鎴栭┏鍥炴湇鍔℃姤鍛?|
| review | HealthInfoReviewTask | reviewTaskId | review_task_id | 鍋ュ悍淇℃伅瀹℃牳浠诲姟 ID | string | yes |  | 鎶ュ憡纭鍚庝骇鐢熺殑褰掓。寤鸿 |
| review | HealthInfoReviewTask | fieldName | field_name | 寤鸿鍙樻洿瀛楁 | string | yes |  | 鍋ュ悍妗ｆ瀛楁鍚?|
| review | HealthInfoReviewTask | reviewStatus | review_status | 瀹℃牳鐘舵€?| string | yes | healthReviewStatus | 绠＄悊绔仴搴蜂俊鎭鏍?|
| common | BaseEntity | createdAt | created_at | 鍒涘缓鏃堕棿 | datetime | yes |  | 鏁版嵁搴撻€氱敤瀹¤瀛楁 |
| common | BaseEntity | updatedAt | updated_at | 鏇存柊鏃堕棿 | datetime | yes |  | 鏁版嵁搴撻€氱敤瀹¤瀛楁 |

## 鐘舵€佸拰鏋氫妇瀛楀吀

| dictCode | dictName | value | label | sort | enabled | remark |
| --- | --- | --- | --- | --- | --- | --- |
| roleCode | 瑙掕壊鏋氫妇 | ELDER | 闀胯緢 | 1 | true | 闀胯緢绔敤鎴?|
| roleCode | 瑙掕壊鏋氫妇 | FAMILY | 瀹跺睘 | 2 | true | 瀹跺睘绔敤鎴?|
| roleCode | 瑙掕壊鏋氫妇 | NURSE | 鎶ょ悊浜哄憳 | 3 | true | 鎶ょ悊绔敤鎴?|
| roleCode | 瑙掕壊鏋氫妇 | ADMIN | 绠＄悊鍛?| 4 | true | 绠＄悊绔鐞嗗憳 |
| roleCode | 瑙掕壊鏋氫妇 | CUSTOMER_SERVICE | 瀹㈡湇 | 5 | true | 瀹㈡湇涓庡伐鍗曞鐞?|
| permissionCode | 鏉冮檺缂栫爜 | ELDER_REMINDER_VIEW | 鏌ョ湅鎻愰啋 | 1 | true | 闀胯緢绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | ELDER_AI_CHAT | AI 瀵硅瘽 | 2 | true | 闀胯緢绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | FAMILY_ELDER_VIEW | 鏌ョ湅闀胯緢妗ｆ | 3 | true | 瀹跺睘绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | FAMILY_ORDER_CREATE | 鍒涘缓鎶ょ悊璁㈠崟 | 4 | true | 瀹跺睘绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | NURSE_ORDER_VIEW | 鏌ョ湅鎶ょ悊璁㈠崟 | 5 | true | 鎶ょ悊绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | NURSE_REPORT_CREATE | 鍒涘缓鏈嶅姟鎶ュ憡 | 6 | true | 鎶ょ悊绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | NURSE_APPEAL_CREATE | 鎻愪氦鐢宠瘔 | 7 | true | 鎶ょ悊绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | ADMIN_DASHBOARD_VIEW | 鏌ョ湅绠＄悊鐪嬫澘 | 8 | true | 绠＄悊绔寜閽潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | ROLE_PERMISSION_MANAGE | 绠＄悊瑙掕壊鏉冮檺 | 9 | true | 绠＄悊绔帴鍙ｆ潈闄?|
| permissionCode | 鏉冮檺缂栫爜 | CUSTOMER_SERVICE_TICKET_HANDLE | 澶勭悊瀹㈡湇宸ュ崟 | 10 | true | 瀹㈡湇绔寜閽潈闄?|
| operationType | 鎿嶄綔绫诲瀷 | UPDATE_ROLE_PERMISSIONS | 鏇存柊瑙掕壊鏉冮檺 | 1 | true | 闃舵 3 绠＄悊绔潈闄愬彉鏇存棩蹇?|
| healthStatus | 鍋ュ悍妫€鏌ョ姸鎬?| UP | 姝ｅ父 | 1 | true | 鏈嶅姟鍙敤 |
| healthStatus | 鍋ュ悍妫€鏌ョ姸鎬?| DOWN | 寮傚父 | 2 | true | 鏈嶅姟涓嶅彲鐢?|
| dictionaryCode | 瀛楀吀缂栫爜 | ALL | 鍏ㄩ儴鏍稿績瀛楀吀 | 1 | true | 瀛楀吀鐩綍鎺ュ彛浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | roleCode | 瑙掕壊鏋氫妇 | 2 | true | 瑙掕壊鍜屾潈闄愪娇鐢?|
| dictionaryCode | 瀛楀吀缂栫爜 | orderStatus | 璁㈠崟鐘舵€?| 3 | true | 棰勭害涓庢姢鐞嗗饱绾︿娇鐢?|
| dictionaryCode | 瀛楀吀缂栫爜 | auditStatus | 瀹℃牳鐘舵€?| 4 | true | 鏂囦欢銆佽祫鏂欏拰璧勮川瀹℃牳浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | bindingStatus | 缁戝畾鐘舵€?| 5 | true | 闀胯緢瀹跺睘缁戝畾浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | reminderStatus | 鎻愰啋鐘舵€?| 6 | true | 鎻愰啋涓績浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | metricStatus | 鎸囨爣鐘舵€?| 7 | true | 鎶ょ悊鎸囨爣鏍￠獙浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | ticketStatus | 宸ュ崟鐘舵€?| 8 | true | 浜哄伐鍗忓姪鍜屽鏈嶄娇鐢?|
| dictionaryCode | 瀛楀吀缂栫爜 | complaintStatus | 鎶曡瘔鐘舵€?| 9 | true | 鎶曡瘔澶勭悊浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | appealStatus | 鐢宠瘔鐘舵€?| 10 | true | 鎶ょ悊鐢宠瘔浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | articleStatus | 鏂囩珷鐘舵€?| 11 | true | 鍩硅鏂囩珷涓婁笅鏋朵娇鐢?|
| dictionaryCode | 瀛楀吀缂栫爜 | accountStatus | 璐﹀彿鐘舵€?| 12 | true | 鐧诲綍璐﹀彿鍚仠浣跨敤 |
| dictionaryCode | 瀛楀吀缂栫爜 | bindingScope | 鎺堟潈鑼冨洿 | 13 | true | 闀胯緢瀹跺睘缁戝畾鎺堟潈鑼冨洿 |
| dictionaryCode | 瀛楀吀缂栫爜 | gender | 鎬у埆 | 14 | true | 闀胯緢鍩虹妗ｆ |
| dictionaryCode | 瀛楀吀缂栫爜 | careLevel | 鐓ф姢绛夌骇 | 15 | true | 闀胯緢鍩虹妗ｆ |
| dictionaryCode | 瀛楀吀缂栫爜 | relationType | 鍏崇郴绫诲瀷 | 16 | true | 闀胯緢瀹跺睘鍏崇郴 |
| dictionaryCode | 瀛楀吀缂栫爜 | serviceStatus | 鏈嶅姟鐘舵€?| 17 | true | 鏈嶅姟椤圭洰涓婁笅鏋?|
| dictionaryCode | 瀛楀吀缂栫爜 | taskStatus | 鎶ょ悊浠诲姟鐘舵€?| 18 | true | 娲惧崟鍜屾姢鐞嗕换鍔″伐浣滃彴 |
| dictionaryCode | 瀛楀吀缂栫爜 | reportStatus | 鏈嶅姟鎶ュ憡鐘舵€?| 19 | true | 鏈嶅姟鎶ュ憡鐢熸垚涓庣‘璁?|
| dictionaryCode | 瀛楀吀缂栫爜 | reportItemType | 鎶ュ憡鏄庣粏绫诲瀷 | 20 | true | 鏈嶅姟鎶ュ憡鏄庣粏鏉ユ簮 |
| dictionaryCode | 瀛楀吀缂栫爜 | ackResult | 鎶ュ憡纭缁撴灉 | 21 | true | 闀胯緢鎴栧灞炵‘璁ゆ湇鍔℃姤鍛?|
| dictionaryCode | 瀛楀吀缂栫爜 | healthReviewStatus | 鍋ュ悍淇℃伅瀹℃牳鐘舵€?| 22 | true | 鍋ュ悍褰掓。寤鸿瀹℃牳 |
| accountStatus | 璐﹀彿鐘舵€?| ENABLED | 鍚敤 | 1 | true | 璐﹀彿鍙櫥褰?|
| accountStatus | 璐﹀彿鐘舵€?| DISABLED | 绂佺敤 | 2 | true | 璐﹀彿涓嶅彲鐧诲綍 |
| accountStatus | 璐﹀彿鐘舵€?| LOCKED | 閿佸畾 | 3 | true | 瀹夊叏绛栫暐閿佸畾 |
| bindingScope | 鎺堟潈鑼冨洿 | HEALTH_VIEW | 鏌ョ湅鍋ュ悍妗ｆ | 1 | true | 瀹跺睘鍙煡鐪嬪仴搴锋。妗?|
| bindingScope | 鎺堟潈鑼冨洿 | HEALTH_EDIT | 缂栬緫鍋ュ悍妗ｆ | 2 | true | 瀹跺睘鍙紪杈戝仴搴锋。妗?|
| bindingScope | 鎺堟潈鑼冨洿 | ORDER_CREATE | 鍒涘缓鎶ょ悊璁㈠崟 | 3 | true | 瀹跺睘鍙唬闀胯緢涓嬪崟 |
| bindingScope | 鎺堟潈鑼冨洿 | REPORT_VIEW | 鏌ョ湅鏈嶅姟鎶ュ憡 | 4 | true | 瀹跺睘鍙煡鐪嬫姤鍛?|
| bindingScope | 鎺堟潈鑼冨洿 | REPORT_CONFIRM | 纭鏈嶅姟鎶ュ憡 | 5 | true | 瀹跺睘鍙‘璁ゆ姤鍛?|
| bindingScope | 鎺堟潈鑼冨洿 | ARCHIVE_EDIT | 缂栬緫褰掓。淇℃伅 | 6 | true | 瀹跺睘鍙淮鎶ゅ綊妗ｄ俊鎭?|
| gender | 鎬у埆 | MALE | 鐢?| 1 | true | 闀胯緢鍩虹妗ｆ |
| gender | 鎬у埆 | FEMALE | 濂?| 2 | true | 闀胯緢鍩虹妗ｆ |
| gender | 鎬у埆 | UNKNOWN | 鏈煡 | 3 | true | 闀胯緢鍩虹妗ｆ |
| careLevel | 鐓ф姢绛夌骇 | LEVEL_1 | 涓€绾х収鎶?| 1 | true | 杞诲害鐓ф姢 |
| careLevel | 鐓ф姢绛夌骇 | LEVEL_2 | 浜岀骇鐓ф姢 | 2 | true | 涓害鐓ф姢 |
| careLevel | 鐓ф姢绛夌骇 | LEVEL_3 | 涓夌骇鐓ф姢 | 3 | true | 閲嶅害鐓ф姢 |
| relationType | 鍏崇郴绫诲瀷 | SON | 鍎垮瓙 | 1 | true | 瀹跺睘鍏崇郴 |
| relationType | 鍏崇郴绫诲瀷 | DAUGHTER | 濂冲効 | 2 | true | 瀹跺睘鍏崇郴 |
| relationType | 鍏崇郴绫诲瀷 | SPOUSE | 閰嶅伓 | 3 | true | 瀹跺睘鍏崇郴 |
| relationType | 鍏崇郴绫诲瀷 | OTHER | 鍏朵粬 | 4 | true | 瀹跺睘鍏崇郴 |
| serviceStatus | 鏈嶅姟鐘舵€?| ON_SHELF | 宸蹭笂鏋?| 1 | true | 瀹跺睘绔彲瑙?|
| serviceStatus | 鏈嶅姟鐘舵€?| OFF_SHELF | 宸蹭笅鏋?| 2 | true | 瀹跺睘绔笉鍙 |
| reportStatus | 鎶ュ憡鐘舵€?| CONFIRMED | 宸茬‘璁?| 1 | true | 鎶ュ憡宸插畬鎴愮‘璁?|
| reportStatus | 鎶ュ憡鐘舵€?| ARCHIVE_REVIEW_PENDING | 寰呭綊妗ｅ鏍?| 2 | true | 瀹跺睘鎺ュ彈寤鸿鍚庤繘鍏ュ仴搴蜂俊鎭鏍?|
| reportStatus | 鎶ュ憡鐘舵€?| PENDING | 寰呭鐞?| 3 | true | 寮傝鎶ュ憡鍥炲埌寰呭鐞?|
| orderStatus | 璁㈠崟鐘舵€?| WAIT_DISPATCH | 寰呮淳鍗?| 1 | true | 璁㈠崟宸叉彁浜わ紝绛夊緟娲惧崟 |
| orderStatus | 璁㈠崟鐘舵€?| DISPATCHED | 宸叉淳鍗?| 2 | true | 绠＄悊绔凡娲剧粰鎶ょ悊浜哄憳 |
| orderStatus | 璁㈠崟鐘舵€?| ACCEPTED | 宸叉帴鍗?| 3 | true | 鎶ょ悊浜哄憳宸叉帴鍗?|
| orderStatus | 璁㈠崟鐘舵€?| ON_THE_WAY | 鍓嶅線涓?| 4 | true | 鎶ょ悊浜哄憳姝ｅ湪鍓嶅線鏈嶅姟鍦板潃 |
| orderStatus | 璁㈠崟鐘舵€?| SERVING | 鏈嶅姟涓?| 5 | true | 鎶ょ悊鏈嶅姟杩涜涓?|
| orderStatus | 璁㈠崟鐘舵€?| WAIT_REPORT | 寰呮姤鍛?| 6 | true | 鏈嶅姟缁撴潫锛岀瓑寰呮姤鍛婄敓鎴?|
| orderStatus | 璁㈠崟鐘舵€?| WAIT_CONFIRM | 寰呯‘璁?| 7 | true | 绛夊緟闀胯緢鎴栧灞炵‘璁?|
| orderStatus | 璁㈠崟鐘舵€?| COMPLETED | 宸插畬鎴?| 8 | true | 璁㈠崟闂幆瀹屾垚 |
| orderStatus | 璁㈠崟鐘舵€?| CANCELED | 宸插彇娑?| 9 | true | 璁㈠崟鍙栨秷 |
| taskStatus | 鎶ょ悊浠诲姟鐘舵€?| DISPATCHED | 宸叉淳鍗?| 1 | true | 绠＄悊绔凡娲剧粰鎶ょ悊浜哄憳 |
| taskStatus | 鎶ょ悊浠诲姟鐘舵€?| ACCEPTED | 宸叉帴鍗?| 2 | true | 鎶ょ悊浜哄憳纭鎺ュ崟 |
| taskStatus | 鎶ょ悊浠诲姟鐘舵€?| ON_THE_WAY | 鍓嶅線涓?| 3 | true | 鎶ょ悊浜哄憳姝ｅ湪鍓嶅線 |
| taskStatus | 鎶ょ悊浠诲姟鐘舵€?| SERVING | 鏈嶅姟涓?| 4 | true | 鎶ょ悊鏈嶅姟杩涜涓?|
| taskStatus | 鎶ょ悊浠诲姟鐘舵€?| COMPLETED | 宸插畬鎴?| 5 | true | 鎶ょ悊浠诲姟瀹屾垚 |
| taskStatus | 鎶ょ悊浠诲姟鐘舵€?| CANCELED | 宸插彇娑?| 6 | true | 璁㈠崟鍙栨秷瀵艰嚧浠诲姟鍙栨秷 |
| reportStatus | 鏈嶅姟鎶ュ憡鐘舵€?| DRAFT | 鑽夌 | 1 | true | 鎶ュ憡鐢熸垚鍓嶈崏绋?|
| reportStatus | 鏈嶅姟鎶ュ憡鐘舵€?| WAIT_CONFIRM | 寰呯‘璁?| 2 | true | 绛夊緟闀胯緢鎴栧灞炵‘璁?|
| reportStatus | 鏈嶅姟鎶ュ憡鐘舵€?| CONFIRMED | 宸茬‘璁?| 3 | true | 鏈嶅姟鎶ュ憡宸茬‘璁?|
| reportStatus | 鏈嶅姟鎶ュ憡鐘舵€?| REJECTED | 宸查┏鍥?| 4 | true | 鎶ュ憡琚暱杈堟垨瀹跺睘椹冲洖 |
| reportItemType | 鎶ュ憡鏄庣粏绫诲瀷 | SERVICE_RECORD | 鏈嶅姟璁板綍 | 1 | true | 鏉ヨ嚜鎶ょ悊鏈嶅姟璁板綍 |
| reportItemType | 鎶ュ憡鏄庣粏绫诲瀷 | VITAL_SIGN | 鐢熷懡浣撳緛 | 2 | true | 鏉ヨ嚜鐢熷懡浣撳緛璁板綍 |
| reportItemType | 鎶ュ憡鏄庣粏绫诲瀷 | NURSING_ADVICE | 鎶ょ悊寤鸿 | 3 | true | 鎶ょ悊浜哄憳寤鸿 |
| reportItemType | 鎶ュ憡鏄庣粏绫诲瀷 | RISK_NOTE | 椋庨櫓鎻愮ず | 4 | true | 椋庨櫓鍜屾敞鎰忎簨椤?|
| ackResult | 鎶ュ憡纭缁撴灉 | ACCEPTED | 宸叉帴鍙?| 1 | true | 纭鏈嶅姟瀹屾垚 |
| ackResult | 鎶ュ憡纭缁撴灉 | REJECTED | 宸查┏鍥?| 2 | true | 瀵规湇鍔℃姤鍛婃湁寮傝 |
| healthReviewStatus | 鍋ュ悍淇℃伅瀹℃牳鐘舵€?| PENDING | 寰呭鏍?| 1 | true | 绛夊緟绠＄悊绔鏍?|
| healthReviewStatus | 鍋ュ悍淇℃伅瀹℃牳鐘舵€?| APPROVED | 宸查€氳繃 | 2 | true | 鍙樻洿寤鸿宸查€氳繃 |
| healthReviewStatus | 鍋ュ悍淇℃伅瀹℃牳鐘舵€?| REJECTED | 宸查┏鍥?| 3 | true | 鍙樻洿寤鸿琚┏鍥?|
| auditStatus | 瀹℃牳鐘舵€?| PENDING | 寰呭鏍?| 1 | true | 涓婁紶鍚庣瓑寰呭鏍?|
| auditStatus | 瀹℃牳鐘舵€?| APPROVED | 宸查€氳繃 | 2 | true | 瀹℃牳閫氳繃 |
| auditStatus | 瀹℃牳鐘舵€?| REJECTED | 宸查┏鍥?| 3 | true | 瀹℃牳椹冲洖 |
| auditStatus | 瀹℃牳鐘舵€?| NEED_MORE | 闇€琛ュ厖 | 4 | true | 瀹℃牳闇€瑕佽ˉ鍏呮潗鏂?|
| bindingStatus | 缁戝畾鐘舵€?| PENDING | 寰呯‘璁?| 1 | true | 瀹跺睘鍙戣捣缁戝畾锛岀瓑寰呴暱杈堢‘璁?|
| bindingStatus | 缁戝畾鐘舵€?| ACTIVE | 宸茬敓鏁?| 2 | true | 缁戝畾鍏崇郴鍙敤 |
| bindingStatus | 缁戝畾鐘舵€?| REJECTED | 宸叉嫆缁?| 3 | true | 闀胯緢鎷掔粷缁戝畾 |
| bindingStatus | 缁戝畾鐘舵€?| REVOKED | 宸叉挙閿€ | 4 | true | 瀹跺睘鎴栭暱杈堟挙閿€鎺堟潈 |
| bindingStatus | 缁戝畾鐘舵€?| EXPIRED | 宸茶繃鏈?| 5 | true | 閭€璇锋垨鎺堟潈杩囨湡 |
| reminderStatus | 鎻愰啋鐘舵€?| PENDING | 寰呮墽琛?| 1 | true | 鎻愰啋灏氭湭澶勭悊 |
| reminderStatus | 鎻愰啋鐘舵€?| DONE | 宸插畬鎴?| 2 | true | 鐢ㄦ埛宸插畬鎴愭彁閱掍簨椤?|
| reminderStatus | 鎻愰啋鐘舵€?| SNOOZED | 绋嶅悗鎻愰啋 | 3 | true | 鐢ㄦ埛閫夋嫨绋嶅悗鎻愰啋 |
| reminderStatus | 鎻愰啋鐘舵€?| NEED_HELP | 璇锋眰鍗忓姪 | 4 | true | 鐢ㄦ埛闇€瑕佷汉宸ュ崗鍔?|
| reminderStatus | 鎻愰啋鐘舵€?| MISSED | 宸查敊杩?| 5 | true | 鎻愰啋鏃堕棿宸茶繃涓旀湭澶勭悊 |
| metricStatus | 鎸囨爣鐘舵€?| PENDING | 寰呮彁浜?| 1 | true | 鎸囨爣灏氭湭鎻愪氦 |
| metricStatus | 鎸囨爣鐘舵€?| SUBMITTED | 宸叉彁浜?| 2 | true | 鎸囨爣宸叉彁浜ゅ緟鏍￠獙 |
| metricStatus | 鎸囨爣鐘舵€?| PASS | 宸茶揪鏍?| 3 | true | 鎸囨爣婊¤冻瑕佹眰 |
| metricStatus | 鎸囨爣鐘舵€?| MISSING | 鏈畬鎴?| 4 | true | 蹇呭～鎸囨爣缂哄け |
| metricStatus | 鎸囨爣鐘舵€?| PENDING_PROOF | 寰呰ˉ璇佹槑 | 5 | true | 鏈畬鎴愬師鍥犻渶瑕佽瘉鏄?|
| metricStatus | 鎸囨爣鐘舵€?| EXEMPT_APPROVED | 璞佸厤閫氳繃 | 6 | true | 绠＄悊绔悓鎰忚眮鍏?|
| metricStatus | 鎸囨爣鐘舵€?| EXEMPT_REJECTED | 璞佸厤椹冲洖 | 7 | true | 绠＄悊绔┏鍥炶眮鍏?|
| ticketStatus | 宸ュ崟鐘舵€?| PENDING | 寰呭鐞?| 1 | true | 宸ュ崟鏂板缓 |
| ticketStatus | 宸ュ崟鐘舵€?| PROCESSING | 澶勭悊涓?| 2 | true | 瀹㈡湇鎴栫鐞嗗憳澶勭悊涓?|
| ticketStatus | 宸ュ崟鐘舵€?| RESOLVED | 宸茶В鍐?| 3 | true | 闂宸茶В鍐?|
| ticketStatus | 宸ュ崟鐘舵€?| CLOSED | 宸插叧闂?| 4 | true | 宸ュ崟鍏抽棴 |
| complaintStatus | 鎶曡瘔鐘舵€?| PENDING | 寰呭鐞?| 1 | true | 鎶曡瘔鏂板缓 |
| complaintStatus | 鎶曡瘔鐘舵€?| PROCESSING | 澶勭悊涓?| 2 | true | 绠＄悊绔鐞嗕腑 |
| complaintStatus | 鎶曡瘔鐘舵€?| RESOLVED | 宸茶В鍐?| 3 | true | 鎶曡瘔宸叉湁澶勭悊缁撴灉 |
| complaintStatus | 鎶曡瘔鐘舵€?| REJECTED | 宸查┏鍥?| 4 | true | 鎶曡瘔涓嶆垚绔?|
| appealStatus | 鐢宠瘔鐘舵€?| PENDING | 寰呭鐞?| 1 | true | 鎶ょ悊浜哄憳宸叉彁浜ょ敵璇?|
| appealStatus | 鐢宠瘔鐘舵€?| APPROVED | 鐢宠瘔閫氳繃 | 2 | true | 绠＄悊绔€氳繃鐢宠瘔 |
| appealStatus | 鐢宠瘔鐘舵€?| REJECTED | 鐢宠瘔椹冲洖 | 3 | true | 绠＄悊绔┏鍥炵敵璇?|
| articleStatus | 鏂囩珷鐘舵€?| DRAFT | 鑽夌 | 1 | true | 绠＄悊绔紪杈戜腑 |
| articleStatus | 鏂囩珷鐘舵€?| PUBLISHED | 宸插彂甯?| 2 | true | 鎶ょ悊绔彲瑙?|
| articleStatus | 鏂囩珷鐘舵€?| OFFLINE | 宸蹭笅绾?| 3 | true | 鎶ょ悊绔笉鍙 |
## 缁存姢瑙勫垯

- 瀛楁杩涘叆涓や釜浠ヤ笂妯″潡鍓嶏紝蹇呴』鍏堝啓鍏ュ瓧娈靛瓧鍏搞€?
- 鐘舵€佸€间竴鏃﹁鎺ュ彛銆佹暟鎹簱鎴?mock 浣跨敤锛屽繀椤诲厛鍐欏叆鐘舵€佸瓧鍏搞€?
- 涓嶅厑璁稿悓涓€鍚箟鍑虹幇澶氫釜瀛楁鍚嶏紝渚嬪 `list`銆乣items`銆乣records` 娣风敤銆?
- 鍒嗛〉鍒楄〃缁熶竴浣跨敤 `records`銆?
- 浠讳綍鍙樻洿蹇呴』鍦?PR 涓鏄庡奖鍝嶈寖鍥淬€?


