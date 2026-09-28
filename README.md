# 澄明 · 企业管理平台

完整的 React + Spring Boot 模块化单体示例，包含用户、角色、部门、权限、字典、操作审计和交付流水线。

## 已实现

- Java 21、Spring Boot 4.0.3、MyBatis-Plus 3.5.15，五个运行模块及一个独立代码生成模块。
- JWT + 数据库会话；退出、禁用账号、密码修改立即撤销会话；角色权限每次请求读取，授权变化即时生效。
- RBAC 五表、内置管理员保护、用户逻辑删除、角色分配。
- ALL / DEPT_TREE / DEPT / SELF 数据范围，多角色取并集；列表、详情、修改、删除均约束数据范围。
- 部门循环检查与引用保护；菜单、按钮、接口权限管理。
- 字典 Redis Cache-Aside，事务提交后失效，持久化补偿重试、延迟二次失效和短 TTL。
- 字典启动时预热；组织展示列表使用短 TTL Redis 缓存并在部门变更提交后失效。数据权限范围仍直接查询数据库。
- AOP 操作审计：独立事务写入 outbox，定时异步转入审计表；不记录请求体、密码、token 或异常正文。
- React + TypeScript + Ant Design Pro 页面；OpenAPI 生成类型并通过 openapi-fetch 调用。
- Flyway、JUnit HTTP 集成测试、MySQL Testcontainers、Playwright、JaCoCo 核心规则门禁。
- Docker Compose、GitHub Actions、备份/恢复/回滚脚本。
- JDBC 元数据 + FreeMarker CRUD 生成器，产出独立的后端与前端脚手架供评审集成。

## 本地启动（不需要 Docker）

安装 Java 21、Maven 3.9、Node 22+，在项目根目录执行：

```bash
bash scripts/dev.sh
```

访问 http://127.0.0.1:5173 。本地模式使用 H2 文件数据库，关闭 Redis 缓存，数据保存在 backend/data。

| 账号 | 密码 | 范围 |
|---|---|---|
| admin | Admin-local-2026! | 全部 |
| manager | Manager-local-2026! | 产品研发部及下级 |
| employee | Employee-local-2026! | 本人 |
| finance | Finance-local-2026! | 本人 |

这些账号仅用于 local 模式。正式模式只初始化 ADMIN_PASSWORD 指定的管理员密码。

## 验证

后端完整门禁（需要 Docker，使用独立 MySQL/Redis 测试容器，不连接业务库）：

```bash
bash scripts/verify-backend.sh
```

该入口执行门禁自测、干净构建、真实数据库/缓存测试、V1 旧数据迁移、实时 OpenAPI 比对和覆盖率检查。关键测试报告缺失、测试数量不足、任意测试失败或跳过都会失败。契约比较只忽略测试随机端口产生的 `servers` 差异，不修改契约快照。当前后端 40 个测试及 6 个门禁自测通过，详情见 [backend-gate.md](docs/backend-gate.md)。

以下命令用于分项验证；未开启 `RUN_MYSQL_TESTS` 的 Maven 运行不算完整后端门禁：

```bash
mvn -B -f backend/pom.xml verify
python3 scripts/check-coverage.py
cd frontend
npm ci
npm run lint
npm run build
npx playwright install chromium
# 先确保后端运行在 8080
npm run test:e2e
```

使用 Docker 验证 MySQL：

```bash
RUN_MYSQL_TESTS=true mvn -B -f backend/pom.xml verify
```

后端运行后更新契约：`bash scripts/export-contract.sh`。接口文档在本地模式的 http://127.0.0.1:8080/swagger-ui/index.html 。

## 正式部署

```bash
cp .env.example .env
# 修改全部密钥；JWT_SECRET 至少 32 字节，ADMIN_PASSWORD 至少 12 字符
APP_VERSION=v1 bash scripts/deploy.sh
```

页面默认监听 127.0.0.1:8088，在服务器前置 HTTPS 反向代理后对外提供服务。数据库与 Redis 不向宿主机开放端口。正式模式默认关闭 OpenAPI 文档。

GitHub Actions 的 verify 在 PR 和 main push 执行。仓库管理员需把 `verify` 设为 main 分支必需状态检查，才能真正禁止失败合并。

## CRUD 代码生成

生成器要求表使用单列 `BIGINT id` 主键，支持常见字符串、整数、布尔、日期和小数列。它读取数据库元数据，输出 Entity、Mapper、Service、Controller 和 React CRUD 页面；生成结果不会自动进入运行模块。

```bash
DB_URL='jdbc:mysql://localhost:3306/shop_admin' DB_USER=shop_admin DB_PASSWORD='...' \
  bash scripts/generate-crud.sh sys_dict dictionaries generated/sys_dict
```

先检查生成结果、补输入校验和业务规则，再复制后端文件到 `admin-system`，配置权限码与菜单，导出 OpenAPI 并将前端页面改为类型化 client 调用。生成页只是起步模板；集成后补真实断言的测试，才进入 CI。已有文件不会被生成器覆盖。

自动 staging：配置带 staging 标签的 self-hosted runner、staging environment、对应 secrets，并设置仓库变量 STAGING_ENABLED=true 和 STAGING_URL。部署后流水线会检查该 URL，并以 15 分钟为超时门槛。代码已关联公开仓库 likesandy/shop-admin；staging 尚未启用，未执行真实上线。

备份：`bash scripts/backup.sh`；恢复：`bash scripts/restore.sh backups/xxx.sql`（会暂停应用写入）；应用回滚：`bash scripts/rollback.sh previous-commit-sha`。数据库迁移不自动逆向执行，采用向后兼容迁移。

## 目录

```
backend/
  admin-common/   响应、错误、审计注解
  admin-auth/     登录、JWT、会话、Security
  admin-system/   用户、组织、权限、字典、数据范围
  admin-audit/    切面、独立事务 outbox、异步转存
  admin-starter/  启动、迁移、配置、集成测试
  admin-generator/  JDBC 元数据与 FreeMarker CRUD 脚手架
frontend/         管理界面与端到端测试
docs/openapi.json API 契约快照
scripts/          开发、契约、覆盖率、部署与恢复
```

## 验证记录与边界

- 本地收尾清单、异常重启和告警验收见 [后端最终验收](docs/backend-final-acceptance.md)。可选指标采集的启动方式见 [监控说明](docs/monitoring.md)。
- 本地 Docker Compose 部署、容器重建持久化及独立环境备份恢复已通过，见 [本地部署验收记录](docs/compose-acceptance.md)。无需测试服务器即可复现；远程 staging 尚未验收。
- 后端当前共 41 个测试通过：H2 HTTP 13 个、MySQL HTTP/并发 17 个、MySQL + Redis 缓存 4 个、数据范围单测 4 个、迁移升级 1 个、生成器 1 个、独立指标认证 1 个，零失败、零跳过。
- 核心类及拆分后的服务/仓储行覆盖率门槛为 70%，包含字典、部门缓存、审计和指标认证；生成器为 50%。门禁入口为 `bash scripts/verify-backend.sh`。
- 前端 TypeScript 类型检查及 Vite 生产构建通过。
- Playwright 管理员与普通员工两条浏览器用例已在本地通过；仍需在 CI 环境复验。
- 缓存为最终一致，TTL 约 5～7 分钟；持久化补偿不能提供强一致语义。
- 审计 outbox 使用同步独立事务持久化，再异步归档；若数据库完全不可用，记录错误日志，不能承诺零丢失。
- 角色/权限/部门写入及角色分配仅内置超级管理员可用；主管不能修改持有高权限角色的用户，避免通过重置密码提权。
- 菜单当前为一级结构，自定义菜单只管理元数据；新增业务页面仍需实现前端页面与后端鉴权。
- 生成器已提供脚手架，但生成业务代码需要人工评审、测试与契约接入。微服务、多租户未实现。

## 契约与交付验收补充

- 表单使用独立 UI 类型和显式请求映射，避免 `any` 绕过 OpenAPI。执行 `cd frontend && npm run test:contract`，会在内存中模拟字段改名、类型变化和新增必填字段，确认生产表单映射拒绝这些破坏性变更；不会修改契约文件。
- `DataScopeRulesTest` 使用 Mockito 独立验证默认拒绝、本人、全部及部门子树与多角色并集。HTTP 集成测试继续覆盖数据库与接口链路。
- 覆盖率检查对 AuthService、Tokens、DataScope 和拆分后的六组业务服务/仓储分别要求 ≥70%，生成器自身 ≥50%。这不等于生成 CRUD 已达到 50%：产物仍需集成到业务模块，补充业务测试并纳入独立覆盖率规则。
- staging 验证页面可访问及未登录 API 返回 401，并从 GitHub workflow 创建时间计算到验收完成的总耗时，包含验证任务和 runner 排队，超过 900 秒判失败。该起点接近 push 触发，但不包含 GitHub 接收事件前的延迟；真实 push 时间验收仍需结合平台事件记录。
- 远程验收：将项目关联目标 GitHub 仓库；main 开启 PR 与必需 `verify` 检查；配置 staging runner、secrets、URL 及 `STAGING_ENABLED=true`；确认 environment 无人工审批要求。用失败测试 PR 验证无法合并，再用成功 main push 留存工作流、staging URL 与耗时证据。本地配置通过不代表以上远程设置已完成。

## 后端职责划分

`SystemController` 保留原 HTTP 路由、鉴权与审计入口，委托 UserService、RoleService、DepartmentService、PermissionService、AuditQueryService、OverviewService 及原有 DictService。原 SystemService 已移除。六组拆分业务各有独立 Repository，封装 SQL 与已有 MyBatis-Plus 调用，事务、业务规则及提交后事件发布仍由 Service 管理。

数据范围由 DataScope 提供，部门展示缓存仍由 DeptCache 管理；本轮未调整认证模块、字典缓存和审计写入链路的职责。用户修改、删除、角色分配、个人改密必须在同一服务事务内先锁用户行，再校验和写入。后端拆分验证记录见 [backend-refactoring.md](docs/backend-refactoring.md)。

## GitHub CI 状态

公开仓库为 [likesandy/shop-admin](https://github.com/likesandy/shop-admin)，主分支 main。[首次完整 CI](https://github.com/likesandy/shop-admin/actions/runs/36397408929) 已通过，包含统一后端门禁、前端验证与镜像构建，staging 未启用。main 已开启分支保护：必须通过 PR、verify 检查通过且分支保持最新，管理员同样受限，禁止强推与删除。细节见 [github-ci.md](docs/github-ci.md)。
