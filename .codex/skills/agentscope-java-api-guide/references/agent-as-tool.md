# Agent As Tool

## What It Is

Agent as Tool registers a sub-agent as a callable tool for another agent.

## Official Example

Official registration example:

```java
Toolkit toolkit = new Toolkit();
toolkit.registerAgentTool(
    new SubAgentTool(provider, config)
);
```

## Stability Note

The official docs explicitly mark this feature as experimental. Treat the API surface as change-prone.

## Core Pattern

The docs register a sub-agent through toolkit registration and require a provider lambda that creates a fresh agent instance for each call.

## Source-Verified Core API

The inspected core jar exposes `io.agentscope.core.tool.subagent.SubAgentTool` directly.

- Register it through `Toolkit.registerAgentTool(...)`
- It implements `AgentTool`
- It is constructed with:
  - `SubAgentProvider<?>`
  - `SubAgentConfig`

## Important Config Points

- provider lambda must create a new sub-agent instance
- `forwardEvents`
- `session`
- `toolName`
- `description`

## Session Behavior

The docs expose `message` and optional `session_id`. If `session_id` is present, the sub-agent can continue earlier conversation state through the configured session backend.

## When To Use It

- expert-agent delegation
- hierarchical agents
- long-lived expert sub-dialogues behind a tool boundary

## Common Pitfalls

- Do not reuse one mutable sub-agent instance across calls.
- Session choice changes whether sub-agent dialogue survives restart.
- Because the feature is experimental, isolate it behind your own abstraction if you need upgrade safety.
- Do not assume `Toolkit.builder()` exists in the core artifact.

## Related Files

- Tool calling: [tool-calling.md](tool-calling.md)
- Session persistence: [session.md](session.md)
- A2A when the boundary must be remote: [a2a.md](a2a.md)

## Official Doc

- <https://java.agentscope.io/zh/multi-agent/agent-as-tool.html>
