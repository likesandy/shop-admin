# 生成业务代码测试闭环

2026-09-28，在 `admin-generated-example` 完成“个人业务记录”示例的后端闭环。示例的五个 Java 文件和 React 脚手架均由当前模板生成，不是手写另一套代码代替生成结果。

## 闭环如何连接

1. `CrudGeneratorTest` 从示例建表 SQL 读取元数据，重新生成代码，与仓库中的示例逐文件比较；模板变化而样例未同步时直接失败。
2. Maven 正常编译示例模块。该模块不被 `admin-starter` 依赖，示例 V3 迁移只在独立测试库执行，生产契约及业务数据库不变。
3. `GeneratedNoteTest` 启动真实 MySQL 8.4 和随机端口应用，经过真实登录、JWT、方法鉴权、MyBatis-Plus、事务及审计组件调用生成的 Controller/Service。
4. 实际 `/v3/api-docs` 与 `contract/openapi.json` 比较，动态端口字段除外；字段、路由或请求结构变化必须显式审阅快照。
5. `npm run test:contract` 从这份 OpenAPI 在内存生成 TypeScript 类型并编译示例客户端。分别模拟字段改名、类型变化、新增必填字段，确认编译拒绝破坏性变更；不修改真实契约文件。
6. 后端门禁要求生成业务测试必须出现且不能跳过，并对生成的 Controller、Service、Entity、Input 分别执行 ≥50% 行覆盖率门禁。

## 业务与安全断言

| 场景 | 已验证行为 |
| --- | --- |
| CRUD | 创建、列表、详情、更新、删除；不存在的记录返回 404 |
| 输入校验 | 标题必填且最长 64 字符，内容最长 255 字符，错误结构返回 400，拒绝后数据库不变 |
| 可空字段 | 显式 null 能清空内容，不受 MyBatis 默认忽略 null 更新策略影响 |
| 失败事务 | 同一归属人标题重复返回 409，原标题与内容不被部分修改；失败审计仍保留 |
| 数据权限 | 普通用户只能查询和读写本人记录；跨用户详情/修改/删除返回 404，不暴露记录是否存在 |
| 防止越权赋值 | 客户端伪造 id、ownerId 不能影响主键或归属；修改时不能转移归属 |
| 管理员 | 可读取、修改、删除其他人的记录；新增记录仍归属当前管理员 |
| 接口权限 | 未登录 401，无权限 403；只读角色不能新增、修改或删除 |
| 契约 | 写入 schema 不包含 id/ownerId；必填、长度及路径与实际接口一致；客户端拒绝不兼容结构 |

模板基于明确约定处理数据范围：`owner_id` 必须为 `BIGINT NOT NULL`；没有该字段时拒绝非管理员访问。它不会猜测任意业务表的部门权限或租户规则。没有业务字段、归属类型不正确或归属允许 null 时拒绝生成。

## 验证结果与命令

```bash
bash scripts/verify-backend.sh
cd frontend
npm run test:contract
npm run lint
```

- 48 个 Java 测试全部通过，无失败、错误或跳过；其中生成器 3 个、生成业务 MySQL 测试 5 个。
- 8 个 Python 门禁测试通过，包括缺失生成业务测试、生成 Service 覆盖率 49% 和缺失报告必须失败。
- 生成的 NoteController、NoteService、NoteEntity、NoteInput 行覆盖率均为 100%，门槛各为 50%；生成器自身为 80.9%。行覆盖率不代表所有并发时序已验证。
- 既有生产客户端契约检查和新增示例客户端契约检查、前端 lint 均通过。

需要刻意更新样例时，先修改模板或示例 SQL，再执行：

```bash
mvn -B -f backend/pom.xml -pl admin-generator -am \
  -Dtest=CrudGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false \
  -DrefreshGeneratedExample=true test
RUN_MYSQL_TESTS=true mvn -B -f backend/pom.xml -pl admin-generated-example -am \
  -Dtest=GeneratedNoteTest -Dsurefire.failIfNoSpecifiedTests=false \
  -DrefreshGeneratedContract=true test
```

审阅生成代码及契约 diff 后，再执行上面的完整门禁。刷新开关只用于主动更新；CI 不传刷新开关，不会悄悄接受模板或契约漂移。

## 范围

这次完成的是一个真实生成模块的后端闭环及类型客户端示例，不代表所有表生成后都具备业务正确性。React 页面仍为待集成脚手架；它没有加入现有页面路由。示例列表最多返回 100 条，所有权固定为本人/管理员，未实现部门共享、租户隔离或复杂业务校验。后续消费生成器时应沿用此验证流程，并替换为对应业务规则。
