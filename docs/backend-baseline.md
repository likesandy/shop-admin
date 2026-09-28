# 后端基线验证 · 2026-09-28

## 范围与环境

本轮聚焦真实依赖环境与关键失败路径，不调整业务架构。使用 Docker 29.6.2、Testcontainers MySQL 8.4 / Redis 7.4-alpine；数据库和 Redis 均为测试专用容器、随机映射端口。HTTP 测试使用随机应用端口，不连接本地业务数据库。

当前 Git 分支为 master，项目文件尚未跟踪；本轮未执行暂存、提交或推送。

## 可复现命令

```bash
RUN_MYSQL_TESTS=true mvn -B -f backend/pom.xml clean verify
python3 scripts/check-coverage.py
```

必须开启 RUN_MYSQL_TESTS，否则真实 MySQL 与 Redis 测试会跳过。覆盖率必须接在 clean verify 成功之后运行，避免引用旧产物。

## 结果

原始基线 26 个测试通过。补充断言后，干净构建约 46 秒，31 个测试全部通过，失败、错误、跳过均为 0：

| 测试类 | 数量 | 环境 |
|---|---:|---|
| FullStackTest | 11 | H2 + 真实 HTTP |
| MySqlTest | 11 | MySQL 8.4 + 真实 HTTP |
| RedisCacheTest | 4 | MySQL 8.4 + Redis 7.4 |
| DataScopeRulesTest | 4 | Mockito 单测 |
| CrudGeneratorTest | 1 | 元数据、生成文件与 Java 编译验证 |

本次覆盖率：AuthService 100%、Tokens 75%、DataScope 100%、SystemService 80%、CrudGenerator 77.9%，现有门禁全部通过。覆盖率是行覆盖率，不能等同于分支或并发场景完备。

## 本轮补充

- RedisCacheTest 从 H2 切换至独立 MySQL 容器，使事务和缓存验证覆盖真实组合。
- 外层事务回滚后，字典及部门新增均撤销，原缓存不被失效，补偿记录随事务回滚。
- 字典类型变更后，新旧两类缓存均失效并正确回填。
- 暂停测试 Redis 容器模拟不可用：写库仍成功，读取回退 MySQL，失效补偿保留；恢复后重试删除模拟的旧值回填并清理任务，验证新缓存有短 TTL。容器恢复放在 finally 中。
- 经 HTTP 发起角色分配，利用无效角色 ID 触发外键失败：原用户角色关联完整回滚，失败审计仍保留，审计表不含测试密码、token 和目标请求用户名。该用例在 H2、MySQL 各运行一次。

本轮未发现需要修改生产代码的已复现缺陷。

## 已有覆盖及剩余边界

已有测试覆盖未登录/非法 token、退出失效、禁用/改密撤销、角色变更即时生效、数据范围并集、跨部门读写删除拒绝、逻辑删除、唯一约束、部门循环和引用保护。两次迁移在空 MySQL 库上成功执行。

尚未验收：

1. 多请求并发登录锁定、权限变更与业务写入之间的竞态、并发部门编辑。
2. 进程崩溃重启后的 outbox 与缓存任务恢复；本轮仅验证数据库持久记录及 Redis 故障恢复，没有模拟应用崩溃。
3. 审计数据库整体不可用时的丢失策略与告警；当前代码仅记录错误日志。
4. 历史版本数据库升级和备份恢复；本轮只验证空库迁移。
5. Redis 缓存保持最终一致；旧值回填用显式写入旧缓存模拟，没有证明任意并发时序下的强一致性。
6. 本轮未执行浏览器、远程 CI 或部署验收；仅修改测试与本报告。

运行中 Flyway 提示 MySQL 8.4 高于该版本已验证的 8.1；本次迁移成功，但依赖兼容性需在后续升级审查中确认。

原始日志保存在本机临时目录 `/tmp/shop-admin-backend-baseline.log` 与 `/tmp/shop-admin-backend-baseline-final.log`，长期依据应以 CI 留存报告为准。
