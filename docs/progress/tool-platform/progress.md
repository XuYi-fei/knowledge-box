# Tool Platform Progress

## 当前阶段

- 用户工具平台已可对外提供基础能力，当前重点是继续扩展工具模板、执行治理和后台配置效率。

## 已完成

- 用户侧已新增 `/tools` 页面与 header `工具` tab，首批内置 `Base64 编码`、`Base64 解码`、`MD5 摘要`。
- 管理端已新增用户工具目录与工具执行日志页面；后端已落地 `app_tool_definition / app_tool_execution_log`、用户工具执行接口、Redis 限流与执行审计日志。
- 用户工具页的输入表单与结果展示已切到 schema 驱动；`SERVER` 工具可直接通过后台元数据驱动前端渲染。
- 已补充 `URL 编码/解码`、`SHA-256 摘要`、`JSON 格式化/压缩`、`时间戳转换` 等通用模板工具，并实现对应 `CLIENT` handler。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖工具页、schema 驱动输入/结果渲染、管理端工具目录与执行日志页面。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖工具定义、执行接口、审计日志与执行器注册链路。
- 后端：`mvn -q -pl backend/backend-app -am -Dtest=Md5DigestAppToolExecutorTests -Dsurefire.failIfNoSpecifiedTests=false test` 可通过。

## 待继续推进

- 扩充工具模板与常用执行器，提升工具页的实际可用性。
- 继续完善执行日志筛选、失败诊断和限流治理。
- 评估更多 `SERVER` 工具的 schema 驱动能力，减少前端为了已有执行器改版的频率。

## 关键注意点

- `SERVER` 工具的字段布局与结果展示应尽量走后台 schema，避免已有执行器每次改元数据都要前端发版。
- 仅新增全新的 `CLIENT` 执行器时，才需要补前端 handler 并重新发版。
- 后端执行型工具要继续保留限流与审计链路，避免平台能力扩展后失去可观测性。
