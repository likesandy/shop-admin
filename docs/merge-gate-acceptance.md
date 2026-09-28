# 合并门禁验收

本 PR 仅用于验收，不合并。

负向测试提交 2dd008e 加入一条故意失败的 JUnit 测试。GitHub run 36398604394 的 verify 实际失败，PR #1 的 mergeStateStatus 为 BLOCKED。

本次修复提交移除故障测试，保留本说明以维持 PR 差异，继续验证完整流水线通过后的合并状态。
