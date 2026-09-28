# 本地 Docker Compose 部署验收

2026-09-28 在本机完成验收，代码基于 `4cacd9f34e22dcdb40091520bcaf605830018971`。使用现有 Dockerfile、`compose.yml` 和部署/备份/恢复脚本，无业务代码修改。

## 隔离方式

- 源环境：Compose 项目 `shop-admin-acceptance`，入口 `127.0.0.1:19888`。
- 恢复环境：Compose 项目 `shop-admin-restore-check`，入口 `127.0.0.1:19889`。
- 两套环境各自拥有 MySQL、Redis、后端、Nginx 前端及独立数据卷；数据库、Redis 和后端没有发布宿主机端口。
- 使用独立随机测试凭据，不在文档或 Git 中保存密码。原有开发服务及其他 Docker 项目未改动。
- 镜像标签为 `acceptance-4cacd9f`，仅用于本次本地验收。

## 实际验证结果

| 检查 | 结果 |
| --- | --- |
| `scripts/deploy.sh` 构建镜像并启动完整环境 | 通过 |
| MySQL、Redis、后端健康检查 | 通过，后端 health 返回 UP |
| Nginx 页面及安全响应头 | HTTP 检查通过；前端容器无独立 healthcheck |
| 未登录请求用户接口 | 返回 401 |
| 管理员登录、生产初始化用户 | 通过，初始用户仅管理员 |
| 创建测试员工、创建和修改字典 | 通过 |
| 员工数据范围及鉴权 | 仅看本人；读取管理员及审计列表返回 403 |
| Redis 字典缓存 | `dict:type:compose_check` 键存在 |
| 审计 | 新增用户、新增字典、修改字典记录存在；检查记录无密码/token 泄露 |
| 删除容器后保留卷重建 | 登录、用户、字典及审计验证通过 |
| 实际备份并恢复到另一套独立 MySQL | 恢复后 HTTP 验证通过 |
| 源数据库与恢复数据库比较 | 迁移版本/成功状态、用户关键字段、角色关联、字典及审计关键字段一致 |

HTTP 验证通过 Nginx 入口调用实际后端、MySQL 和 Redis，未使用模拟接口。测试员工为 `compose_employee`，测试字典类型为 `compose_check`，修改后标签为“持久化成功”。数据库比较覆盖上述相关字段，不代表整个数据库逐字节相等。

## 执行流程

临时辅助程序从受限权限文件读取测试环境变量，再调用项目脚本；没有将凭据写入命令参数。复现时需先设置独立的 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`、`JWT_SECRET` 和 `ADMIN_PASSWORD`。

```bash
export COMPOSE_PROJECT_NAME=shop-admin-acceptance
export HTTP_PORT=19888
export APP_VERSION=acceptance-4cacd9f
bash scripts/deploy.sh
# 经 HTTP 创建用户、字典并验证鉴权及审计
docker compose down
docker compose up -d --no-build --wait --wait-timeout 240
# 再次验证持久化数据
bash scripts/backup.sh

export COMPOSE_PROJECT_NAME=shop-admin-restore-check
export HTTP_PORT=19889
docker compose up -d --no-build --wait mysql redis
# 仅对这套独立恢复环境执行，导入会替换目标表
bash scripts/restore.sh backups/shop-admin-20260928-165945.sql
# 验证恢复环境的 HTTP 行为并比较两套数据库相关字段
```

本次备份为 `backups/shop-admin-20260928-165945.sql`，大小 14,415 字节，文件权限 `600`，目录权限 `700`。已将 `backups/` 加入 `.gitignore`，避免 SQL 数据进入公开仓库。

## 收尾与边界

两套测试项目均已执行 `docker compose down`，容器和网络已移除；未使用 `-v`，四个测试数据卷和 SQL 备份保留。本次临时检查脚本及凭据位于 `/tmp/shop-admin-compose-acceptance/`，仅供本机复查，临时目录不保证长期保留。

本次证明本机容器化部署、重建持久化和备份恢复可用，不代表远程 staging、公网部署、15 分钟自动交付或旧应用版本回滚已验收。本次未新增浏览器交互测试，也未重新运行全量单元测试。未提交或推送本轮改动。
