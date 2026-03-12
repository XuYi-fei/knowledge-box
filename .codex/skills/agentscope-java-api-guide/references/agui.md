# AG-UI

## What It Is

AG-UI is the protocol integration path for exposing AgentScope agents to web frontends.

## Official Example

Official Spring registration example from the docs:

```java
@Bean
public AguiAgentRegistryCustomizer myAguiAgent() {
    return registry -> registry.register(
        "travel-agent",
        context -> ReActAgent.builder()
            .name("travel-agent")
            .sysPrompt("You are a travel assistant")
            .description("A travel assistant")
            .model(openAIChatModel)
            .build()
    );
}
```

## Dependencies Mentioned In Docs

- `agentscope-agui-spring-boot-starter`
- `spring-boot-starter-webflux`

## Source-Verified Core Surface

The inspected core jar exposes:

- `AguiAgentAdapter`
- `AguiAdapterConfig`
- `AguiRequestProcessor`
- `AguiAgentRegistry`
- `AguiEventType`

The tutorial helper `AguiAgentRegistryCustomizer` is not present in the inspected core jar.

## Core Registration API

In the core artifact, the registry surface is:

```java
AguiAgentRegistry registry = new AguiAgentRegistry();
registry.register("travel-agent", agent);
registry.registerFactory("travel-agent-factory", () -> agent);
```

## Typical Usage Path

1. Create or obtain an `Agent`.
2. Register it in `AguiAgentRegistry`.
3. Adapt it through `AguiAgentAdapter`.
4. Configure runtime behavior with `AguiAdapterConfig`.
5. Process requests with `AguiRequestProcessor` if you need request normalization or id resolution.

## Source-Verified Event Notes

`AguiEventType` includes:

- `RUN_STARTED`
- `RUN_FINISHED`
- `TEXT_MESSAGE_START`
- `TEXT_MESSAGE_CONTENT`
- `TEXT_MESSAGE_END`
- `TOOL_CALL_START`
- `TOOL_CALL_ARGS`
- `TOOL_CALL_END`
- `TOOL_CALL_RESULT`
- `STATE_SNAPSHOT`
- `STATE_DELTA`
- `RAW`
- `CUSTOM`
- `REASONING_START`
- `REASONING_MESSAGE_START`
- `REASONING_MESSAGE_CONTENT`
- `REASONING_MESSAGE_END`
- `REASONING_MESSAGE_CHUNK`
- `REASONING_END`

## Why It Matters

Use AG-UI when you want a browser or frontend framework to talk to AgentScope agents through a standard protocol instead of inventing a custom streaming contract.

## Common Pitfalls

- AG-UI is frontend exposure, not the same thing as A2A.
- `server-side-memory` changes conversation ownership and lifecycle; decide whether state belongs on client or server.
- Do not enable CORS casually without matching your real deployment boundary.
- Do not assume Spring helper types from the docs are part of the core artifact.

## Related Files

- Session persistence: [session.md](session.md)
- Studio for debugging: [studio.md](studio.md)
- A2A for agent-to-agent protocols: [a2a.md](a2a.md)

## Official Doc

- <https://java.agentscope.io/zh/task/agui.html>
