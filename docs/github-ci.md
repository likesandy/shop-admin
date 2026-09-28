# GitHub CI 接入

仓库：https://github.com/likesandy/shop-admin（公开），默认分支 main。

`.github/workflows/ci.yml` 在 PR 和 main/master push 时运行稳定名为 `verify` 的任务：统一后端门禁、前端 lint/契约检查/构建、浏览器测试和容器镜像构建。后端日志经 tee 保存，pipefail 保留门禁失败退出码；所有模块 Surefire、JaCoCo、前端测试报告和应用日志在成功或失败时均尝试上传，保留 14 天。

工作流已通过 actionlint 1.7.7；staging 是显式配置的自托管 runner 标签。staging 仍需 main push、验证成功且仓库变量 STAGING_ENABLED=true 才运行，本次未启用。

## 分支保护

仓库公开后已成功启用 main 分支保护：必须通过 PR，verify 为必需检查，分支需保持最新，管理员同样受限，禁止强推和删除。仓库此前为私有时的套餐限制已解除。

## 云端实际验收

首个代码提交 aa6aec8 的 CI 已完整成功：https://github.com/likesandy/shop-admin/actions/runs/36397408929 。verify 包含后端 40 个测试、6 个门禁自测、覆盖率、前端 lint/契约/构建、两条浏览器用例以及前后端 Docker 镜像构建。verification-reports 已上传；staging 明确跳过。

本报告随后的纯文档提交使用 [skip ci]，避免为验收记录重复执行整条流水线；被验收的应用与工作流代码没有变化。

## 分支保护实际验收（2026-09-28）

独立验收 PR：https://github.com/likesandy/shop-admin/pull/1 ，验收后已关闭，未合并。

- 失败提交 `2dd008e` 加入临时 JUnit 失败用例；[运行 36398604394](https://github.com/likesandy/shop-admin/actions/runs/36398604394) 的 verify 为 FAILURE，GitHub mergeStateStatus 为 BLOCKED。
- 修复提交 `1fcc737` 删除临时用例；[运行 36398945951](https://github.com/likesandy/shop-admin/actions/runs/36398945951) 的完整 verify 为 SUCCESS，GitHub mergeStateStatus 恢复 CLEAN，mergeable 为 MERGEABLE。
- 通过读取 GitHub 合并状态验收，未调用实际合并接口；没有合并任何故障或验收改动，main 仍为 `4cacd9f34e22dcdb40091520bcaf605830018971`。staging 未启用。
- 上述 main SHA 是门禁验收时的快照；后续本地收尾改动通过独立 PR 交付。
