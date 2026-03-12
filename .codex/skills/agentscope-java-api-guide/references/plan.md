# Planning

## What The Docs Emphasize

The planning docs describe explicit task decomposition. In the inspected core artifact, the concrete planning center is `PlanNotebook`.

## Official Example

Official planner example from the docs:

```java
ReWooPlanner planner = ReWooPlanner.builder()
    .chatModel(chatModel)
    .maxPlanTurns(3)
    .executor(Executors.newWorkStealingPool())
    .build();

PlanningOutput output = planner.generate("What is the current weather in Hangzhou?");
```

## Source-Verified Core Planning Surface

The following planning types are present in the inspected core jar:

- `PlanNotebook`
- `PlanNotebook.Builder`
- `PlanStorage`
- `InMemoryPlanStorage`
- `Plan`
- `SubTask`
- `PlanToHint`

The following planner names are not present in the inspected core jar:

- `ReWooPlanner`
- `ParallelPlanner`

## Minimal Core Pattern

```java
PlanNotebook notebook = PlanNotebook.builder()
    .storage(new InMemoryPlanStorage())
    .maxSubtasks(8)
    .needUserConfirm(false)
    .build();
```

## When To Use Planning

- the task is decomposable into explicit sub-steps
- you want predictable execution structure before tool calls
- multiple independent branches can run in parallel
- you need better inspectability than a pure hidden-chain-of-thought loop

## Practical Guidance

- Use planning when the toolset is stable and the task benefits from decomposition.
- In the core artifact, the operational API is on `PlanNotebook`, not on a separate planner facade.
- Feed the planner accurate tool and capability descriptions, or the plan quality drops quickly.

## Source-Verified Behavior Notes

- `PlanNotebook` itself is a `StateModule`, so it can be saved into a session.
- It exposes methods such as:
  - `createPlan(...)`
  - `createPlanWithSubTasks(...)`
  - `reviseCurrentPlan(...)`
  - `updateSubtaskState(...)`
  - `finishSubtask(...)`
  - `finishPlan(...)`
  - `getCurrentHint()`
- `enablePlan()` on `ReActAgent.Builder` is a real convenience method in the core artifact.

## Common Pitfalls

- Planning adds latency and tokens. Skip it for simple single-tool tasks.
- Bad tool descriptions produce bad plans.
- If execution state must survive pauses, combine planning with the continuation or workflow-state patterns.
- If you need exact code-level planning APIs, use `PlanNotebook`, not only the tutorial names.

## Related Files

- ReAct agent orchestration: [react-agent.md](react-agent.md)
- State and workflow context: [state-session.md](state-session.md)
- Tool calling: [tool-calling.md](tool-calling.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/planning/>
