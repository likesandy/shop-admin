# GitHub CI 接入

仓库：https://github.com/likesandy/shop-admin（私有），默认分支 main。

`.github/workflows/ci.yml` 在 PR 和 main/master push 时运行稳定名为 `verify` 的任务：统一后端门禁、前端 lint/契约检查/构建、浏览器测试和容器镜像构建。后端日志经 tee 保存，pipefail 保留门禁失败退出码；所有模块 Surefire、JaCoCo、前端测试报告和应用日志在成功或失败时均尝试上传，保留 14 天。

工作流已通过 actionlint 1.7.7；staging 是显式配置的自托管 runner 标签。staging 仍需 main push、验证成功且仓库变量 STAGING_ENABLED=true 才运行，本次未启用。

## 分支保护限制

配置 main 的 PR 要求与必需 verify 检查时，GitHub 返回 HTTP 403：私有仓库需要升级 GitHub Pro 或改为公开。为保持项目私有，未改变可见性，也未购买套餐。因此 CI 自动检查与报告已接入，但“失败禁止合并”尚未强制生效，不能据此宣称合并门禁验收完成。

启用支持私有仓库保护的套餐后，应设置：必须通过 PR、verify 为必需状态检查、合并前分支保持最新、管理员同样受限、禁止强推及删除主分支。然后用失败 PR 验证无法合并，再修复为绿灯。该步骤需平台能力支持，不可用单纯工作流 YAML 替代。

本次没有创建故意失败的 PR：缺少分支保护时，只能证明任务变红，不能证明合并被阻止。

## 云端实际验收

首个代码提交 aa6aec8 的 CI 已完整成功：https://github.com/likesandy/shop-admin/actions/runs/36397408929 。verify 包含后端 40 个测试、6 个门禁自测、覆盖率、前端 lint/契约/构建、两条浏览器用例以及前后端 Docker 镜像构建。verification-reports 已上传；staging 明确跳过。

本报告随后的纯文档提交使用 [skip ci]，避免为验收记录重复执行整条流水线；被验收的应用与工作流代码没有变化。
