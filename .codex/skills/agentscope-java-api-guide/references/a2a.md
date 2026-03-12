# A2A

## What The Docs Cover

The official A2A tutorial shows how AgentScope Java exposes and consumes agent capabilities through the A2A protocol.

## Official Example

Official example from the docs:

```java
Agent weatherAgent = CommonAgent.builder()
    .name("weather-agent")
    .description("提供天气查询服务")
    .url("http://localhost:10000")
    .model(model)
    .skill(new WeatherSkill())
    .build();
```

## Source-Verified Core A2A Surface

The inspected core jar exposes these concrete A2A classes:

- `AgentScopeA2aServer`
- `AgentScopeA2aServer.Builder`
- `ReActAgentWithBuilderRunner`
- `AgentRequestOptions`
- `AgentExecuteProperties`

The following tutorial names are not present in the inspected core jar:

- `CommonAgent`
- `ConsumerAgent`
- `ExecutorAgent`
- `A2AAgentExecutor`
- `A2AAgentSkill`

## Practical View

- use `AgentScopeA2aServer.builder(ReActAgent.builder()...)` when you want to expose a `ReActAgent` through A2A
- use `ReActAgentWithBuilderRunner.newInstance(...)` when you want a runner built from a `ReActAgent.Builder`
- use `AgentRequestOptions` for `taskId`, `sessionId`, and `userId`
- use `AgentExecuteProperties` to shape executor behavior

## Source-Verified Builder Notes

`AgentScopeA2aServer.Builder` in the inspected core jar supports:

- `agentCard(...)`
- `withTransport(...)`
- `taskStore(...)`
- `queueManager(...)`
- `pushConfigStore(...)`
- `pushSender(...)`
- `executor(...)`
- `deploymentProperties(...)`
- `withAgentRegistry(...)`
- `agentExecuteProperties(...)`

## What Matters Most

- define clear agent metadata and capability description
- keep request and response contracts stable
- decide whether the agent is exposing a skill-like API or a richer interactive protocol
- handle network concerns explicitly: port, timeout, retry, and authentication

## Common Pitfalls

- A2A does not remove the need for schema discipline.
- Exposed capability descriptions drive discoverability; vague cards are hard to route to.
- Remote calls add latency and failure modes, so surface timeout behavior clearly.
- If the remote capability triggers tools or long workflows, think through cancellation and continuation early.
- Do not copy tutorial class names into core-only code without checking the artifact.

## Related Files

- Agent skill packaging: [agent-skill.md](agent-skill.md)
- Structured output for contracts: [structured-output.md](structured-output.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/a2a/>
