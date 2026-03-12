# Overview

## Scope

This skill is scoped to Maven dependency:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope</artifactId>
    <version>1.0.9</version>
</dependency>
```

## Naming Notes

- The official Java class is `ReActAgent`, not `ReactAgent`.
- "ask-user" in this doc set maps to Human in the Loop plus Continuation.
- "state", "session", and "memory" are separate concerns in AgentScope Java and are documented across different pages.
- The current doc site was last updated on `2026-01-03` per the official intro page. Some pages still show `1.0.8` in installation snippets, so treat API pages as the source of truth and align dependency version with your project.

## Read This Before Loading More Files

- ReAct agent and agent hooks live together in the agent guide.
- Tool calling, RAG, memory, planning, structured output, A2A, and agent skill each have separate tutorial pages.
- State and session management are fragmented across:
  - workflow context
  - message management
  - continuation
- When class names, builders, or exact method names matter, also load [source-code-map.md](source-code-map.md). That file is based on the local `agentscope-1.0.9.jar` and javadoc, not only on tutorial prose.

## Local Artifact Inspection

The local machine currently has:

- `agentscope-1.0.9.jar`
- `agentscope-1.0.9-javadoc.jar`
- `agentscope-1.0.9-sources.jar`

So this skill now uses a mixed strategy:

- official docs for concepts and examples
- local artifact and source inspection for package names, builder methods, and API signatures

If the two disagree, prefer the local artifact for coding guidance and explicitly call out the mismatch.

## Source Map

- Installation: <https://java.agentscope.io/zh/guide/installation/>
- Quickstart installation: <https://java.agentscope.io/zh/quickstart/installation/>
- Intro: <https://java.agentscope.io/zh/intro.html>
- Agent and hooks: <https://java.agentscope.io/zh/guide/agent/>
- Agent configuration: <https://java.agentscope.io/zh/task/agent-config.html>
- Model: <https://java.agentscope.io/zh/task/model.html>
- Tool: <https://java.agentscope.io/zh/task/tool.html>
- MCP: <https://java.agentscope.io/zh/task/mcp.html>
- Tool calling: <https://java.agentscope.io/zh/tutorial/builtin-tool/>
- RAG: <https://java.agentscope.io/zh/tutorial/rag/>
- Human in the loop: <https://java.agentscope.io/zh/tutorial/human-in-the-loop/>
- Continuation: <https://java.agentscope.io/zh/tutorial/continuation/>
- Memory: <https://java.agentscope.io/zh/tutorial/memory/>
- Planning: <https://java.agentscope.io/zh/tutorial/planning/>
- State: <https://java.agentscope.io/zh/task/state.html>
- Session: <https://java.agentscope.io/zh/task/session.html>
- Workflow context: <https://java.agentscope.io/zh/tutorial/workflow-context/>
- Message management: <https://java.agentscope.io/zh/tutorial/message-management/>
- Multimodal: <https://java.agentscope.io/zh/task/multimodal.html>
- Structured output: <https://java.agentscope.io/zh/tutorial/structured-output/>
- Studio: <https://java.agentscope.io/zh/task/studio.html>
- AG-UI: <https://java.agentscope.io/zh/task/agui.html>
- Agent skill: <https://java.agentscope.io/zh/tutorial/agent-skill/>
- A2A: <https://java.agentscope.io/zh/tutorial/a2a/>
- Pipeline: <https://java.agentscope.io/zh/multi-agent/pipeline.html>
- MsgHub: <https://java.agentscope.io/zh/multi-agent/msghub.html>
- Agent as Tool: <https://java.agentscope.io/zh/multi-agent/agent-as-tool.html>
- Multi-Agent Debate: <https://java.agentscope.io/zh/multi-agent/multiagent-debate.html>
- AI coding: <https://java.agentscope.io/zh/task/ai-coding.html>

## Quick Routing

- Need exact builder or method signatures: read [source-code-map.md](source-code-map.md)
- User says "ReactAgent": read [react-agent.md](react-agent.md)
- User says "模型选型" or "formatter": read [model.md](model.md)
- User says "技能封装" or "远程 skill": read [agent-skill.md](agent-skill.md)
- User says "工具调用": read [tool-calling.md](tool-calling.md)
- User says "MCP": read [mcp.md](mcp.md)
- User says "知识库" or "RAG": read [rag.md](rag.md)
- User says "hook": read [hooks.md](hooks.md)
- User says "人工介入" or "ask-user": read [hitl.md](hitl.md) and [state-session.md](state-session.md)
- User says "记忆": read [memory.md](memory.md)
- User says "规划": read [plan.md](plan.md)
- User says "状态": read [state.md](state.md) and [state-session.md](state-session.md)
- User says "会话": read [session.md](session.md) and [state-session.md](state-session.md)
- User says "多模态": read [multimodal.md](multimodal.md)
- User says "结构化输出": read [structured-output.md](structured-output.md)
- User says "Studio" or "可视化调试": read [studio.md](studio.md)
- User says "AG-UI" or "前端集成": read [agui.md](agui.md)
- User says "A2A": read [a2a.md](a2a.md)
- User says "Pipeline": read [pipeline.md](pipeline.md)
- User says "MsgHub": read [msghub.md](msghub.md)
- User says "子智能体工具": read [agent-as-tool.md](agent-as-tool.md)
- User says "多智能体辩论": read [multi-agent-debate.md](multi-agent-debate.md)
- User says "llms.txt" or "AI 编程": read [ai-coding.md](ai-coding.md)
