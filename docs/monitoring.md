# 本地指标采集与基础告警

默认部署不启用额外采集账户。使用 `compose.monitoring.yml` 时，后端启用仅匹配 `/actuator/prometheus` 的独立 Basic 认证链，用户名为 `metrics`，密码至少 32 个字符。采集凭据不能访问业务 API，也不是数据库中的用户。未启用时仍保留原有认证策略。

先按 README 设置数据库、Redis、JWT、管理员密码等部署变量，再设置独立的随机采集密码：

```bash
export METRICS_PASSWORD="$(openssl rand -hex 24)"
docker compose -f compose.yml -f compose.monitoring.yml up -d --build --wait
```

密码通过环境变量注入，不写入仓库。Prometheus 启动入口将其写入容器内权限为 `600` 的临时文件，采集配置只引用该文件。后端指标端口不发布至宿主机，Nginx 不代理 `/actuator`；Basic 认证仅在 Compose 网络内使用。

Prometheus 页面为 `http://127.0.0.1:9090`，可通过 `PROMETHEUS_PORT` 调整端口。仅绑定本机地址，数据保留 7 天，使用独立 `prometheus_data` 卷。

## 验证内容

- Targets 页面：`shop-admin` 应为 UP。
- 查询 `up{job="shop-admin"}`：应为 1。
- 查询 `jvm_memory_used_bytes{job="shop-admin"}`：应返回 JVM 指标。
- `ShopAdminBackendDown`：采集失败持续 30 秒后转为 FIRING，采集恢复后消退；规则评估和采集周期均为 5 秒。
- 该告警检测的是采集失败，包括后端停止、网络或凭据异常，不等同于所有业务故障。

本轮只验收 Prometheus 告警状态，未接 Alertmanager、邮件或聊天通知，也未设置外部接收人。

## 一键隔离验收

```bash
python3 scripts/verify-recovery.py
```

脚本生成全新的随机 Compose 项目、凭据、镜像标签及本机端口，构建当前源码；同时验证审计崩溃恢复、缓存补偿恢复、指标采集及告警触发/消退。故障注入只针对脚本创建的容器。成功或失败后均尝试删除该项目的容器、网络和测试数据卷，构建镜像仍保留；不连接现有业务数据库。需要 Docker、Python 3 和镜像/依赖下载网络。

该脚本会强制停止测试后端，不应改成复用生产项目。耗时包括镜像构建和至少 30 秒的告警等待，不计入普通后端门禁。
