---
name: agentscope-java-api-guide
description: Quick guide for AgentScope Java 1.0.9 APIs. Use when users ask about io.agentscope:agentscope 1.0.9, ReActAgent (often mistyped as ReactAgent), agent skill, tool calling, RAG, hooks, ask-user or human-in-the-loop, memory, planning, state management, session management, structured output, or A2A.
---

# AgentScope Java API Guide

Use this skill for quick, API-first explanations of AgentScope Java `io.agentscope:agentscope:1.0.9`.

Interpret `ReactAgent` as [`ReActAgent`](references/react-agent.md) unless the user clearly means frontend React.

## Workflow

1. Read only the reference files that match the user's topic.
2. If the request mixes topics, load only those files, not the whole folder.
3. Answer in API terms first: core classes, builder or annotation entry points, lifecycle, and caveats.
4. When the user asks about "state", "session", or "memory", clarify which layer they mean before going deep:
   - component state serialization
   - persisted session storage
   - message history or conversation scope
   - workflow shared state
   - persisted continuation or paused task recovery
   - long-term memory storage
5. When API names or builder methods matter, check [references/source-code-map.md](references/source-code-map.md) first. It is derived from the local `agentscope-1.0.9.jar`, `javadoc`, and `sources.jar`, and is more reliable than memory or tutorial wording.
6. Prefer official `java.agentscope.io/zh` links in the answer.

## Topic Map

- Version and source map: [references/overview.md](references/overview.md)
- Local artifact source inspection: [references/source-code-map.md](references/source-code-map.md)
- Model providers and formatter selection: [references/model.md](references/model.md)
- ReAct agent: [references/react-agent.md](references/react-agent.md)
- Agent skill: [references/agent-skill.md](references/agent-skill.md)
- Tool calling: [references/tool-calling.md](references/tool-calling.md)
- MCP: [references/mcp.md](references/mcp.md)
- RAG: [references/rag.md](references/rag.md)
- Hooks: [references/hooks.md](references/hooks.md)
- Human in the loop / ask-user: [references/hitl.md](references/hitl.md)
- Memory management: [references/memory.md](references/memory.md)
- Planning: [references/plan.md](references/plan.md)
- State serialization: [references/state.md](references/state.md)
- Session persistence: [references/session.md](references/session.md)
- State and session management: [references/state-session.md](references/state-session.md)
- Multimodal: [references/multimodal.md](references/multimodal.md)
- Structured output: [references/structured-output.md](references/structured-output.md)
- Studio: [references/studio.md](references/studio.md)
- AG-UI: [references/agui.md](references/agui.md)
- A2A: [references/a2a.md](references/a2a.md)
- Pipeline: [references/pipeline.md](references/pipeline.md)
- MsgHub: [references/msghub.md](references/msghub.md)
- Agent as Tool: [references/agent-as-tool.md](references/agent-as-tool.md)
- Multi-Agent Debate: [references/multi-agent-debate.md](references/multi-agent-debate.md)
- AI coding docs integration: [references/ai-coding.md](references/ai-coding.md)

## Output Expectations

- Keep answers concise and implementation-oriented.
- If the official topic page includes example code, keep at least one clearly labeled official example snippet in that topic file.
- For each topic, cover:
  - what to use
  - minimal usage path
  - when to use it
  - common pitfalls
- If the docs are fragmented, say so explicitly and point to the related reference files instead of pretending there is one canonical page.
