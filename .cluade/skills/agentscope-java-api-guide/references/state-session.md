# State And Session Management

## Read This File Carefully

AgentScope Java does not present all "state" and "session" topics in one page. The official docs split them across state, session, workflow context, message management, and continuation.

## Layer 1: Workflow Shared State

The official docs split several concepts here, but the inspected core jar is centered around `StateModule`, `StatePersistence`, `Session`, and `SessionManager`.

## Layer 2: Conversation Scope And Message Session

In the core artifact, conversation persistence is usually achieved by binding stateful components to a `SessionKey` and a `Session` backend, not by a dedicated `ConversationContextUtil` helper.

## Layer 3: Pause, Resume, Recovery

For the inspected core artifact, the concrete recovery surface you can rely on is:

- `StateModule.saveTo(...)`
- `StateModule.loadFrom(...)`
- `StateModule.loadIfExists(...)`
- `SessionManager`
- `InterruptContext`
- `ToolSuspendException`

## How To Choose

- Need component serialization contract: use state APIs.
- Need persisted storage backend for agent or memory state: use session APIs.
- Need one coordinator over several stateful components: use `SessionManager`.
- Need paused-task recovery or resume after ask-user: persist the involved `StateModule`s through a session backend.
- Need durable user or runtime context reuse: use memory, not workflow state.

## Source-Verified Core Additions

- `StatePersistence` is a record used by `ReActAgent.Builder.statePersistence(...)`.
- Its flags are:
  - `memoryManaged`
  - `toolkitManaged`
  - `planNotebookManaged`
  - `statefulToolsManaged`
- `SimpleSessionKey.of(...)` is the built-in session-key helper.
- `SessionManager.forSessionId(...)` is the main session orchestration entry point in the inspected core jar.

## Common Pitfalls

- Do not mix workflow state with memory just because both are "stateful."
- Do not assume workflow-context helper classes shown in docs are part of the core artifact unless you verified the module.
- If you need restart recovery, save the actual stateful components, not only a conversation id string.
- Save task or conversation identifiers outside the immediate call stack if the run can outlive the request thread.

## Related Files

- State serialization: [state.md](state.md)
- Session persistence: [session.md](session.md)
- Human in the loop: [hitl.md](hitl.md)
- Memory management: [memory.md](memory.md)

## Official Docs

- <https://java.agentscope.io/zh/task/state.html>
- <https://java.agentscope.io/zh/task/session.html>
- <https://java.agentscope.io/zh/tutorial/workflow-context/>
- <https://java.agentscope.io/zh/tutorial/message-management/>
- <https://java.agentscope.io/zh/tutorial/continuation/>
