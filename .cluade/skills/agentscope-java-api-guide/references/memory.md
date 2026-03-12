# Memory Management

## What The Docs Cover

The official memory tutorial discusses multiple memory patterns. The inspected core artifact is centered around `Memory`, `InMemoryMemory`, long-term memory hooks, and auto-context memory.

## Official Example

Official example from the docs:

```java
Memory memory = MemoryFactory.create(
    new MemoryConfig("memory1", MemoryType.InMemoryMemory)
);
```

Official Redis-backed example:

```java
RedisMemory memory = RedisMemory.builder()
    .id("memory4")
    .redisURI("redis://localhost:6379")
    .build();
```

## Source-Verified Core Types

- `Memory`: the main interface
- `InMemoryMemory`: in-process memory and also a `StateModule`
- `LongTermMemory`: async long-term retrieval and recording contract
- `LongTermMemoryMode`: `AGENT_CONTROL`, `STATIC_CONTROL`, `BOTH`
- `StaticLongTermMemoryHook`: built-in hook for static long-term-memory injection
- `AutoContextMemory`: memory plus context offloading and compression
- `AutoContextHook`: hook companion for auto-context mode

The following names are not present in the inspected core jar:

- `MemoryFactory`
- `RedisMemory`
- `MapDBMemory`
- `DiskMemory`

## How To Think About It

Use memory for runtime context that should survive more than one single model call, for example:

- recent interaction history
- user profile or preferences
- tool observations worth reusing
- intermediate working context

## Backend Selection

- choose `InMemoryMemory` for local experiments or single-instance runtime
- choose `AutoContextMemory` when chat history can grow large and you want compression/offloading
- choose a `Session` backend if you need persisted memory across restarts
- choose a `LongTermMemory` implementation when you want semantic recall instead of raw transcript persistence

## Source-Verified Behavior Notes

- `Memory` only guarantees:
  - `addMessage(...)`
  - `getMessages()`
  - `deleteMessage(int)`
  - `clear()`
- `InMemoryMemory` can be persisted because it implements `saveTo(...)` and `loadFrom(...)`.
- `AutoContextMemory` can:
  - `compressIfNeeded()`
  - `offload(...)`
  - `reload(...)`
  - `attachPlanNote(...)`

## Common Pitfalls

- Keep stored objects serializable if your chosen strategy needs serialization.
- Do not dump entire transcripts or large documents into memory without summarization or pruning.
- Separate memory from knowledge base. Memory is runtime context; RAG knowledge is retrievable external knowledge.
- Be explicit about retention and eviction, especially for user-specific memory.
- Do not assume doc-site memory backends exist in the core artifact.

## Related Files

- RAG separation: [rag.md](rag.md)
- Workflow or session state: [state-session.md](state-session.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/memory/>
