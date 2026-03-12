# Session

## What It Is

Session provides persistent storage and recovery for agent state across runs.

## Official Example

Official save-and-load example:

```java
Agent agent = buildAgent();
Session session = new JsonSession(Paths.get("target/session"));
String sessionId = "session-123";

agent.saveTo(session, sessionId);
agent.loadFrom(session, sessionId);
```

## Core APIs

- `JsonSession`
- `InMemorySession`
- `MysqlSession`
- `JedisSession`
- `RedissonSession`
- `Session`
- `SessionManager`
- `agent.saveTo(session, sessionId)`
- `agent.loadFrom(session, sessionId)`
- `agent.loadIfExists(session, sessionId)`

## Source-Verified Core API

From the inspected core artifact:

- `Session` exposes:
  - `save(...)`
  - `get(...)`
  - `getList(...)`
  - `exists(...)`
  - `delete(...)`
  - `listSessionKeys()`
- `JsonSession` is constructor-based, not builder-based:
  - `new JsonSession()`
  - `new JsonSession(Path)`
- `JedisSession.builder()` and `RedissonSession.builder()` both exist.
- `MysqlSession` is constructor-based and can be wired from a `DataSource`.
- `SessionManager` is the main helper when you want one place to manage multiple `StateModule`s:

```java
SessionManager manager = SessionManager.forSessionId("session-123")
    .withSession(new InMemorySession())
    .addComponent(agent)
    .addComponent(memory);

manager.loadIfExists();
manager.saveSession();
```

## Backend Selection

- `JsonSession`: file-based persistence; good default when you need restart survival
- `InMemorySession`: test and single-process temporary use
- `MysqlSession`: relational persistence when you already have a JDBC datasource
- `JedisSession` / `RedissonSession`: Redis-backed persistence
- custom `Session`: implement when you need another DB or remote storage

## Important Safety Note

The official docs explicitly warn that `JsonSession` uses `sessionId` as a directory name. If that value comes from untrusted input, validate and sanitize it to block path traversal and separator injection.

## Operational Notes

- newer session formats store one session per directory
- list-like state uses append-friendly formats such as JSONL
- persistence layout is not the same as older all-in-one JSON formats

## Common Pitfalls

- `InMemorySession` loses state on restart and is not a distributed-session solution.
- Do not trust raw `sessionId` values from cookies or query parameters.
- If you migrate from older storage formats, check compatibility before reading old data.
- The core artifact does not expose `JsonSession.builder()`. Use constructors instead.

## Related Files

- State contract: [state.md](state.md)
- Agent as Tool session reuse: [agent-as-tool.md](agent-as-tool.md)
- State and session chooser: [state-session.md](state-session.md)

## Official Doc

- <https://java.agentscope.io/zh/task/session.html>
