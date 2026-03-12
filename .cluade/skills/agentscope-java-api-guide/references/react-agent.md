# ReActAgent

## What It Is

`ReActAgent` is the Java agent implementation for think-act-observe style orchestration. The official docs place it under the general agent guide.

## Official Example

Official quickstart example:

```java
DashScopeChatModel dashScopeChatModel = DashScopeChatModel.builder()
    .apiKey(System.getenv("DASHSCOPE_API_KEY"))
    .modelName("qwen-max")
    .build();

Agent weatherAgent = ReActAgent.builder()
    .name("weatherAssistant")
    .sysPrompt("You are a helpful weather assistant")
    .description("一个查询天气的助手")
    .model(dashScopeChatModel)
    .build();
```

## Core Builder Entry

The official example page shows a builder shape like this:

```java
ReActAgent agent = ReActAgent.builder()
    .name("travelAssistant")
    .sysPrompt("你是一个旅游出行助手...")
    .description("一个旅游出行助手")
    .parallelToolCalls(true)
    .maxIterations(5)
    .model(model)
    .build();
```

## Source-Verified Builder Surface

From the inspected `agentscope-1.0.9.jar`, the real builder methods include:

- `name(...)`
- `description(...)`
- `sysPrompt(...)`
- `model(...)`
- `toolkit(...)`
- `memory(...)`
- `maxIters(...)`
- `hook(...)`
- `hooks(...)`
- `enableMetaTool(...)`
- `modelExecutionConfig(...)`
- `toolExecutionConfig(...)`
- `structuredOutputReminder(...)`
- `planNotebook(...)`
- `skillBox(...)`
- `longTermMemory(...)`
- `longTermMemoryMode(...)`
- `statePersistence(...)`
- `enablePlan()`
- `knowledge(...)`
- `knowledges(...)`
- `ragMode(...)`
- `retrieveConfig(...)`
- `toolExecutionContext(...)`

Important correction: the core artifact exposes `maxIters(int)`, not `maxIterations(int)`.

## Key Fields

- `name`: agent identity, logs, and downstream selection context.
- `sysPrompt`: long-lived role and behavioral constraints.
- `description`: especially important when the agent may be selected by another agent, skill box, or A2A layer.
- `model(model)`: use a model that actually supports the features you expect, especially tools and structured output.
- `toolkit(...)`: the actual tool registry bound to the agent.
- `maxIters(5)`: cap the ReAct loop and prevent runaway tool-agent loops.
- `enablePlan()` or `planNotebook(...)`: enable built-in planning support.
- `knowledge(...)` / `ragMode(...)` / `retrieveConfig(...)`: bind RAG directly at the builder layer.
- `statePersistence(...)`: control which subcomponents are session-managed.

## Source-Verified Runtime Notes

- `ReActAgent` extends `StructuredOutputCapableAgent`, so structured output is not a separate agent type.
- The agent itself implements session-oriented persistence through:
  - `saveTo(Session, SessionKey)`
  - `loadFrom(Session, SessionKey)`
- Streaming is inherited from `AgentBase.stream(...)`, and the event model is:
  - `REASONING`
  - `TOOL_RESULT`
  - `HINT`
  - `AGENT_RESULT`
  - `SUMMARY`
  - `ALL`

## When To Use It

- Tool-using assistants
- Multi-step reasoning with observation feedback
- Agent-as-skill or agent-as-A2A endpoint

## Common Pitfalls

- Do not confuse `ReactAgent` with `ReActAgent`.
- Do not copy tutorial method names blindly. Check the core builder surface first.
- Do not set `maxIters` too high unless you also control tool idempotency and latency.
- If the agent is only doing plain chat, a simpler agent may be cheaper and easier to debug.
- Descriptions matter. Weak `description` text harms routing quality when the agent is reused as a capability node.
- If you need exact signatures, also read [source-code-map.md](source-code-map.md).

## Related Files

- Hooks: [hooks.md](hooks.md)
- Tool calling: [tool-calling.md](tool-calling.md)
- Planning: [plan.md](plan.md)

## Official Doc

- <https://java.agentscope.io/zh/guide/agent/>
- <https://java.agentscope.io/zh/quickstart/agent.html>
