# 后端职责拆分 · 2026-09-28

## 结果

原 SystemService 已移除，按六个业务职责拆成 Service / Repository。SystemController 保留原路由、方法名称、入参、返回类型、鉴权与审计注解，直接委托对应服务。

| 服务 | 职责 | 数据访问 |
|---|---|---|
| UserService | 用户查询、增删改、角色分配、个人改密及会话撤销 | UserRepository，保留 UserMapper |
| RoleService | 角色维护、内置角色保护、角色权限关系 | RoleRepository |
| DepartmentService | 部门树校验、引用保护、范围过滤及缓存变更事件 | DepartmentRepository；展示读取复用 DeptCache |
| PermissionService | 权限维护、父子约束、可见菜单 | PermissionRepository |
| AuditQueryService | 审计分页参数与查询 | AuditQueryRepository |
| OverviewService | 当前用户可见统计 | OverviewRepository、DepartmentService、AuditQueryRepository |

六个服务不直接使用 JdbcClient 或 UserMapper。SQL、分页查询和基础持久化操作放到仓储层；业务校验、事务、授权范围应用及事件发布留在服务层。DataScope 仍是原有数据权限组件；DictService、认证模块、审计写入模块未在本轮重新分层。

## 保留的关键语义

- 用户修改、删除、角色分配和改密仍由服务的 `@Transactional` 包裹；用户行锁是事务中的首次数据库读取，随后才校验范围和高权限角色。
- 用户插入与默认角色分配、角色替换的删除与插入、用户修改与会话撤销仍在同一事务。
- UserMapper 继续负责用户实体 CRUD 和逻辑删除，未将全项目 SQL 改成 MyBatis-Plus。
- 部门树锁、循环校验及引用检查保留；DeptCache.Changed 仍在事务内发布，提交后才清理缓存，回滚不会失效。
- 审计入口继续在 Controller，服务事务完成后才记录结果。仓储层没有独立事务，不会拆开原有原子操作。
- 未修改表结构、迁移、前端或 OpenAPI 契约。

## 两轮验证

每轮执行 `RUN_MYSQL_TESTS=true mvn -B -f backend/pom.xml clean verify` 与 `python3 scripts/check-coverage.py`。

第一轮只拆用户模块：35 个测试通过，用户服务 96.7%、用户仓储 86.4%；使用隔离应用完成浏览器登录、编辑测试员工姓名、保存及列表回读。

第二轮拆剩余职责：39 个测试通过，零失败、零错误、零跳过。

- H2 HTTP：13 个。
- MySQL HTTP/并发：17 个，包含上轮新增的登录锁定和角色分配竞态回归。
- MySQL + Redis：4 个，保留事务回滚、提交后失效及故障补偿验证。
- 数据范围单测：4 个；生成器测试：1 个。

本轮新增两条 HTTP 场景，在 H2/MySQL 各执行一次：工作台与菜单遵守当前用户权限范围；权限父子关联拒绝自引用、非菜单父级、嵌套菜单及存在子项时删除。

实时 OpenAPI 与 `docs/openapi.json`、第一轮运行版本逐项比较，仅排除 `servers` 中临时端口差异，其余 JSON 完全一致。

## 覆盖率门禁

原 SystemService 的门禁替换为六组 Service / Repository 各自 ≥70%，没有降低门槛或只检查旧类。

| 类 | 行覆盖率 |
|---|---:|
| AuthService / Tokens / DataScope | 100% / 75% / 100% |
| UserService / UserRepository | 96.7% / 86.4% |
| RoleService / RoleRepository | 92.3% / 76.2% |
| DepartmentService / DepartmentRepository | 100% / 100% |
| PermissionService / PermissionRepository | 100% / 100% |
| AuditQueryService / AuditQueryRepository | 100% / 100% |
| OverviewService / OverviewRepository | 100% / 100% |
| CrudGenerator | 77.9%，门槛 50% |

覆盖率为行覆盖率，不代表分支、并发时序或安全场景完全覆盖。

## 浏览器第二轮回归

使用本机 15173 前端代理与 18080 后端，后端仅监听 127.0.0.1、使用 H2 内存数据库。原 5173 / 8080 应用不参与测试。

实际新增部门、角色及菜单权限，逐项确认列表回读；审计页面显示三次操作均成功；工作台统计为 4 个用户、5 个部门、4 个角色、3 条审计，与实际数据一致。第一轮截图为 `evidence/split-user.png`，第二轮为 `evidence/split-domains-audit.png`。

临时数据随测试应用退出清除。没有 Git 暂存、提交、推送或部署；当前仓库文件仍处于尚未跟踪状态。历史报告中的 SystemService 名称和覆盖率是当时记录，本报告与 README 描述当前结构。

## 边界

这是职责重构，未改变“撤销操作者权限不会主动取消在途请求”、缓存最终一致、审计数据库整体不可用时可能丢失等已记录边界。Controller 仍是统一 HTTP 入口，以保持契约与审计方法名称稳定；后续若拆 Controller，应再次比较契约。

本机临时构建日志：`/tmp/shop-admin-split-user.log`、`/tmp/shop-admin-split-domains.log`。
