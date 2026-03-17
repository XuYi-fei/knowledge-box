# Chat Progress

## 当前阶段

- 聊天主链路已稳定可用，当前重点是继续打磨流式颗粒度、未完成会话恢复和 Markdown/代码块展示细节。

## 已完成

- 用户侧已具备登录后的知识库问答、会话持久化、历史恢复、删除会话与 SSE 流式输出。
- 聊天主链路已切到 AgentScope Java ReActAgent，支持前置知识检索、tool calling、reasoning/tool/citation 展示与 trace。
- 回答下方引用已内联展示，并可跳转到公开文档详情查看正文，不再依赖右侧单独资料栏。
- 对话区已补齐稳定高度链与内部滚动约束；消息增多时只在会话主区和历史列表内滚动，不再把整页持续撑高。
- 回答下方“关联资料”摘要已压缩为更紧凑的两行预览，减少单条消息的纵向占用。
- 聊天主消息区已补齐细滚动条与右侧内边距，避免粗滚动条与靠右的用户消息头像发生视觉重叠；引用详情页“返回对话”按钮已移到左上区域，返回路径更符合阅读流。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页、引用展示、会话区固定高度与内部滚动。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页细滚动条、引用详情页左上返回按钮等本次 UI 修复。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖聊天编排与引用链路。

## 待继续推进

- 继续打磨流式输出颗粒度与异常/未知流事件的兜底展示。
- 补齐未完成会话恢复与断链后的用户感知。
- 继续优化 Markdown、代码块与长回答的阅读体验。

## 关键注意点

- AgentScope 事件分支需显式覆盖 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`，不要依赖默认分支兜底。
- `@ToolParam` 必须显式声明 `name`。
- AgentScope 工具执行可能切线程，关键上下文不要只依赖 `ThreadLocal`。
