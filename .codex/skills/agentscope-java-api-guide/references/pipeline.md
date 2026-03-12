# Pipeline

## What It Is

Pipeline provides composition patterns for multi-agent workflows.

## Official Example

Official sequential-pipeline example:

```java
SequentialPipeline pipeline = Pipelines.sequential(
    englishAgent,
    chineseAgent
);
Msg result = pipeline.call(Msg.of("user", "Hello"));
```

## Core Types

- `SequentialPipeline`
- `FanoutPipeline`
- `Pipelines`

## Main Factory Methods

The docs highlight:

- `Pipelines.sequential(...)`
- `Pipelines.fanout(...)`
- `Pipelines.fanoutSequential(...)`
- `Pipelines.createSequential(...)`
- `Pipelines.createFanout(...)`

## When To Use It

- a fixed multi-agent chain is enough
- orchestration is simpler than full workflow state machines
- you want quick composition over sequential or fanout patterns

## Practical Guidance

- `SequentialPipeline` is for dependency chains.
- `FanoutPipeline` is for multiple agents processing the same input.
- Use the reusable pipeline objects when the same composition repeats.

## Common Pitfalls

- Pipeline is composition sugar, not a full persistence or recovery solution.
- Fanout concurrency only helps if your model and downstream tools can tolerate it.
- In multi-agent scenarios, remember formatter choice from the model layer.

## Related Files

- Model formatter selection: [model.md](model.md)
- MsgHub: [msghub.md](msghub.md)
- Multi-agent debate: [multi-agent-debate.md](multi-agent-debate.md)

## Official Doc

- <https://java.agentscope.io/zh/multi-agent/pipeline.html>
