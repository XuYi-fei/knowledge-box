# Multi-Agent Debate

## What It Is

Multi-agent debate is a workflow pattern where multiple solver agents exchange views and an aggregator or moderator decides the result.

## Official Example

Official debate loop example:

```java
for (int round = 0; round < maxRounds; round++) {
    for (Agent agent : debaters) {
        Msg msg = agent.call(questionMsg);
        msgHub.broadcast(msg);
    }
}
```

## What The Docs Emphasize

- multiple debaters propose and critique answers
- an aggregator agent decides convergence or final output
- this is useful for reasoning tasks that benefit from contrasting viewpoints

## When To Use It

- difficult reasoning problems
- design review or risk analysis from multiple perspectives
- situations where one agent's first answer is often brittle

## Common Pitfalls

- It is expensive in tokens and latency.
- If all agents share nearly identical prompts and models, debate diversity may be fake.
- You still need a clear aggregation rule or stopping condition.

## Related Files

- Pipeline: [pipeline.md](pipeline.md)
- MsgHub: [msghub.md](msghub.md)
- Planning: [plan.md](plan.md)

## Official Doc

- <https://java.agentscope.io/zh/multi-agent/multiagent-debate.html>
