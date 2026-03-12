# State

## What It Is

State is the serialization contract underneath AgentScope session persistence.

## Official Example

The official docs discuss custom state modules conceptually. For exact coding shape in the core artifact, use the source-verified notes below.

## Core Interfaces

- `StateModule`: implemented by components that can save or load themselves
- `State`: marker interface for serializable state objects
- `SessionKey`
- `SimpleSessionKey`

## Source-Verified Core API

From the inspected core jar:

- `State` is a marker interface.
- `StateModule` provides default methods:
  - `saveTo(Session, SessionKey)`
  - `saveTo(Session, String)`
  - `loadFrom(Session, SessionKey)`
  - `loadFrom(Session, String)`
  - `loadIfExists(Session, SessionKey)`
  - `loadIfExists(Session, String)`
- `SimpleSessionKey` is the concrete built-in key type:

```java
SessionKey key = SimpleSessionKey.of("session-123");
```

- Built-in stateful components confirmed in the core jar include:
  - `ReActAgent`
  - `InMemoryMemory`
  - `PlanNotebook`
  - `SkillBox`

## Recommended Usage

The official docs recommend using `agent.saveTo(...)` and `agent.loadFrom(...)` in most cases instead of manually pushing state items into a session.

Use direct session APIs only when you need custom state layout or you are writing your own state-aware component.

## Built-In Examples Mentioned In Docs

The docs note that `ReActAgent`, `InMemoryMemory`, and `PlanNotebook` already implement `StateModule`.

## Common Pitfalls

- Do not confuse `State` with business DTOs that are never meant to be persisted.
- Keep custom `State` objects stable across versions if they may be reloaded later.
- State is the data contract; session is the storage backend. Do not blur the two.
- If you want selective persistence, also check `StatePersistence` in [state-session.md](state-session.md).

## Related Files

- Session persistence: [session.md](session.md)
- Planning notebook persistence: [plan.md](plan.md)

## Official Doc

- <https://java.agentscope.io/zh/task/state.html>
