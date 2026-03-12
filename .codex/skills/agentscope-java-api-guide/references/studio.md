# Studio

## What It Is

Studio is the official visual debugging and trace inspection UI for AgentScope Java.

## Official Example

Official Studio startup example:

```java
StudioManager.initialize();

Agent weatherAgent = ReActAgent.builder()
    .name("weatherAssistant")
    .sysPrompt("You are a helpful weather assistant")
    .description("一个查询天气的助手")
    .model(dashScopeChatModel)
    .addHook(new StudioMessageHook())
    .build();
```

## Core APIs Mentioned In Docs

- `StudioManager`
- `StudioMessageHook`
- `StudioUserAgent`

## Minimal Usage Path

1. Start the Studio server.
2. Initialize `StudioManager`.
3. Add `StudioMessageHook` to your agent.
4. Optionally use `StudioUserAgent` to receive user input from the web UI.
5. Shut down the studio client cleanly after the run.

## When To Use It

- interactive debugging
- trace and message inspection
- comparing multiple experimental runs
- quick demos without building a custom frontend

## Common Pitfalls

- Studio is observability infrastructure, not a replacement for application session design.
- Remember to shut down the studio client to avoid hanging resources.
- If your app already has its own hooks, make sure Studio hooks do not mask or reorder critical logic unexpectedly.

## Related Files

- Hooks: [hooks.md](hooks.md)
- AG-UI for production frontend exposure: [agui.md](agui.md)

## Official Doc

- <https://java.agentscope.io/zh/task/studio.html>
