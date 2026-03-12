# MsgHub

## What It Is

`MsgHub` is the broadcast center for multi-agent conversations.

## Official Example

Official MsgHub example:

```java
MsgHub msgHub = MsgHub.builder()
    .participant(alice)
    .participant(bob)
    .participant(carol)
    .build();
```

## Core Value

It removes the need to manually fan out each agent's output to every other participant.

## Typical Usage

The docs show using `MsgHub.builder().participants(...).build()` and entering the hub scope before agents call each other.

## When To Use It

- conversational multi-agent collaboration
- shared discussion room patterns
- cases where manual `observe(...)` wiring is too noisy

## Common Pitfalls

- MsgHub is about message distribution, not business arbitration.
- If every agent should not hear every message, MsgHub may be the wrong abstraction.
- Combine it with multi-agent formatters so provider prompts reflect the true speaker history.

## Related Files

- Model formatter selection: [model.md](model.md)
- Pipeline: [pipeline.md](pipeline.md)
- Multi-agent debate: [multi-agent-debate.md](multi-agent-debate.md)

## Official Doc

- <https://java.agentscope.io/zh/multi-agent/msghub.html>
