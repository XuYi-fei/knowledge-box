# Hooks

## What They Are

Agent hooks let you inject logic around agent execution. The dedicated official Hook page uses a unified `onEvent(HookEvent)` model.

## How They Are Added

Hooks are attached from builders such as `ReActAgent.Builder.hook(...)` and `ReActAgent.Builder.hooks(...)`.

## Official Example

Official hook example from the docs:

```java
public class MyAgentHook implements AgentHook {
    @Override
    public void onEvent(HookEvent event) {
        if (event instanceof PreCallEvent) {
            System.out.println("Before agent call");
        } else if (event instanceof PostCallEvent) {
            System.out.println("After agent call");
        }
    }
}
```

## Source-Verified Event Model

The inspected core artifact exposes a single `HookEventType` enum with these values:

- `PRE_CALL`
- `POST_CALL`
- `PRE_REASONING`
- `POST_REASONING`
- `REASONING_CHUNK`
- `PRE_ACTING`
- `POST_ACTING`
- `ACTING_CHUNK`
- `PRE_SUMMARY`
- `POST_SUMMARY`
- `SUMMARY_CHUNK`
- `ERROR`

The hook interface is:

```java
public interface Hook {
    <T extends HookEvent> Mono<T> onEvent(T event);
    default int priority();
}
```

## Good Hook Use Cases

- audit and observability
- prompt or request enrichment
- response post-processing
- metrics, tracing, or policy checks
- lightweight guardrails before and after agent execution

## Practical Guidance

- Keep hooks small and deterministic.
- Use hooks for cross-cutting concerns, not for core business logic.
- If the hook may block on a user decision, switch to HITL instead of hiding that interaction inside a hook.
- Treat streaming hooks as replay-sensitive: duplicate emissions can happen during retries or reconnection paths.

## Common Pitfalls

- Heavy hooks make latency hard to explain.
- Mutating shared state inside hooks can produce hidden coupling.
- If a hook changes prompts or messages, log that clearly for debugging.
- Do not put irreversible side effects in a hook that might rerun on failure.
- When method names matter, prefer the enum-based event model over tutorial class names.

## Related Files

- ReAct agent: [react-agent.md](react-agent.md)
- Human in the loop: [hitl.md](hitl.md)
- State and continuation: [state-session.md](state-session.md)

## Official Doc

- <https://java.agentscope.io/zh/guide/agent/>
- <https://java.agentscope.io/zh/task/hook.html>
