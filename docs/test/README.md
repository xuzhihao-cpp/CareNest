# docs/test

存放测试用例、问题清单、联调记录和阶段测试报告。

建议文件：

- `test-cases.md`：功能测试用例。
- `bug-list.md`：问题跟踪清单。
- `integration-notes.md`：联调记录。

当前可执行的真实联调脚本：

- `phase-19-20-full-stack-integration.mjs`
- `phase-41-55-full-stack-integration.mjs`

阶段41-55脚本通过 Docker Nginx 入口登录五个角色，验证 AI 安全分流、
客服工单与回访、家属投诉、护理申诉与评分、培训阅读、随访、看板、
健康检查，并直接核对 MySQL、操作日志和 Redis。
