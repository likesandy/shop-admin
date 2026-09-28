# 后端本地工程门禁 · 2026-09-28

## 统一入口

```bash
bash scripts/verify-backend.sh
```

依赖 Java 21、Maven、Python 3 和可用的 Docker。脚本切换至仓库目录，先检查工具和 Docker，执行 6 个门禁自测，再强制设置 RUN_MYSQL_TESTS=true 运行 Maven clean verify。随后检查 Surefire 报告与 JaCoCo 覆盖率。任何一步失败都返回非零退出码。

脚本不接受自定义 Maven 参数，避免正常使用时误传跳过或过滤测试选项。独立运行报告检查不能证明产物新鲜，完整验收须使用统一入口的干净构建。脚本不启动常驻应用、不使用固定端口、不连接业务数据库；测试容器由 Testcontainers 管理。clean 会清理各模块 target 构建目录。

## 拦截规则

- FullStackTest、MySqlTest、RedisCacheTest、DataScopeRulesTest、MigrationUpgradeTest、CrudGeneratorTest 必须均有报告，数量至少达到当前基线。
- 所有发现的测试套件均不得为空、失败、报错或跳过；XML 汇总与 testcase 数量须一致；还逐条检查 failure/error/skipped 节点。
- 缺少报告、格式损坏、过滤必需测试、重复套件均失败。新增测试时应同步提高 check-test-reports.py 的基线数量。
- 各核心服务/仓储行覆盖率 ≥70%，生成器 ≥50%，沿用已有覆盖率门禁。
- H2 和 MySQL 的 contractAndHealth 测试均读取实时 /v3/api-docs，与仓库 docs/openapi.json 比较完整 JSON；只删除顶层 servers 后比较。路径、字段、响应和鉴权描述变化均会失败。该检查覆盖后端快照一致性，前端生成类型与编译仍由原前端验证流程负责。

通过 scripts/tests/test_backend_gate.py 验证完整报告可通过，以及缺少 MySQL、Redis 单条跳过、迁移失败、过滤测试、覆盖率为零均会被拒绝。故障输入全部生成在临时目录，不修改真实报告。

## V1 旧数据升级验证

MigrationUpgradeTest 使用独立 MySQL 8.4 容器：

1. 先将 Flyway target 固定为 1，仅执行 V1。
2. 写入旧用户、密码摘要占位值、用户角色关联、自定义字典和旧审计记录。
3. 使用全部迁移升级，确认仅新增执行一条迁移，当前版本为 V2，并通过 Flyway validate。
4. 逐项验证旧数据仍在；向新增 audit_outbox 表写入记录；验证角色外键和关联唯一约束仍生效。
5. 新建 Flyway 实例模拟再次初始化，确认迁移执行数为 0，历史表仍只有两次成功记录，新旧数据未重复或丢失。

这验证迁移初始化的幂等性，不等同于生产应用崩溃恢复、滚动部署兼容或实际备份恢复演练。

## 本次结果与边界

完整门禁通过：40 个 Java 测试，零失败、零错误、零跳过；6 个 Python 门禁自测通过；覆盖率全部达标。临时执行日志为 /tmp/shop-admin-gate.log，最终入口复验日志为 /tmp/shop-admin-gate-final.log。

本轮仅修改验证脚本、测试和文档，未修改业务行为、数据库迁移文件或契约内容，未运行浏览器测试。未提交、推送或部署，也未配置 GitHub 远程分支保护。

后续 CI 接入已将 .github/workflows/ci.yml 改为调用统一入口，并始终上传所有模块的 Surefire/JaCoCo 报告及日志。远程运行与分支保护需另行验收，本节上方为本地门禁阶段的历史记录。
