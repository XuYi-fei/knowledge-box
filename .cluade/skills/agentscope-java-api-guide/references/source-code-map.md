# Source Code Map

## Inspection Basis

This reference is based on the local Maven artifacts already present on the machine:

- `~/.m2/repository/io/agentscope/agentscope/1.0.9/agentscope-1.0.9.jar`
- `~/.m2/repository/io/agentscope/agentscope/1.0.9/agentscope-1.0.9-javadoc.jar`
- `~/.m2/repository/io/agentscope/agentscope/1.0.9/agentscope-1.0.9-sources.jar`

This page is built from:

- class listing via `jar tf`
- signature inspection via `javap`
- javadoc HTML presence checks
- direct source inspection via `unzip -p ...sources.jar`

Use this page when method-level accuracy matters more than tutorial prose.

## Verified Topic To Package Map

- ReAct agent:
  - `io.agentscope.core.ReActAgent`
  - `io.agentscope.core.ReActAgent$Builder`
  - `io.agentscope.core.agent.AgentBase`
  - `io.agentscope.core.agent.StreamOptions`
  - `io.agentscope.core.agent.Event`
  - `io.agentscope.core.agent.EventType`
- Tool calling:
  - `io.agentscope.core.tool.Tool`
  - `io.agentscope.core.tool.ToolParam`
  - `io.agentscope.core.tool.Toolkit`
  - `io.agentscope.core.tool.AgentTool`
  - `io.agentscope.core.tool.ToolExecutionContext`
  - `io.agentscope.core.tool.ToolCallParam`
  - `io.agentscope.core.tool.ToolGroup`
  - `io.agentscope.core.tool.mcp.McpTool`
  - `io.agentscope.core.tool.subagent.SubAgentTool`
- Hooks and streaming:
  - `io.agentscope.core.hook.Hook`
  - `io.agentscope.core.hook.HookEvent`
  - `io.agentscope.core.hook.HookEventType`
- Memory:
  - `io.agentscope.core.memory.Memory`
  - `io.agentscope.core.memory.InMemoryMemory`
  - `io.agentscope.core.memory.LongTermMemory`
  - `io.agentscope.core.memory.LongTermMemoryMode`
  - `io.agentscope.core.memory.StaticLongTermMemoryHook`
  - `io.agentscope.core.memory.autocontext.AutoContextMemory`
  - `io.agentscope.core.memory.autocontext.AutoContextHook`
- Planning:
  - `io.agentscope.core.plan.PlanNotebook`
  - `io.agentscope.core.plan.storage.PlanStorage`
  - `io.agentscope.core.plan.storage.InMemoryPlanStorage`
  - `io.agentscope.core.plan.model.Plan`
  - `io.agentscope.core.plan.model.SubTask`
  - `io.agentscope.core.plan.hint.PlanToHint`
- State and session:
  - `io.agentscope.core.state.State`
  - `io.agentscope.core.state.StateModule`
  - `io.agentscope.core.state.StatePersistence`
  - `io.agentscope.core.state.SessionKey`
  - `io.agentscope.core.state.SimpleSessionKey`
  - `io.agentscope.core.session.Session`
  - `io.agentscope.core.session.SessionManager`
  - `io.agentscope.core.session.InMemorySession`
  - `io.agentscope.core.session.JsonSession`
  - `io.agentscope.core.session.mysql.MysqlSession`
  - `io.agentscope.core.session.redis.jedis.JedisSession`
  - `io.agentscope.core.session.redis.redisson.RedissonSession`
- RAG:
  - `io.agentscope.core.rag.Knowledge`
  - `io.agentscope.core.rag.GenericRAGHook`
  - `io.agentscope.core.rag.KnowledgeRetrievalTools`
  - `io.agentscope.core.rag.RAGMode`
  - `io.agentscope.core.rag.model.RetrieveConfig`
  - `io.agentscope.core.rag.knowledge.SimpleKnowledge`
- Structured output:
  - `io.agentscope.core.agent.StructuredOutputCapableAgent`
  - `io.agentscope.core.agent.StructuredOutputHook`
  - `io.agentscope.core.model.StructuredOutputReminder`
  - `io.agentscope.core.formatter.ResponseFormat`
- Agent skill:
  - `io.agentscope.core.skill.AgentSkill`
  - `io.agentscope.core.skill.SkillBox`
  - `io.agentscope.core.skill.SkillHook`
  - `io.agentscope.core.skill.repository.AgentSkillRepository`
- Human in the loop:
  - `io.agentscope.core.agent.user.UserAgent`
  - `io.agentscope.core.agent.user.UserInputBase`
  - `io.agentscope.core.agent.user.StreamUserInput`
  - `io.agentscope.core.interruption.InterruptContext`
  - `io.agentscope.core.tool.ToolSuspendException`
- A2A:
  - `io.agentscope.core.a2a.server.AgentScopeA2aServer`
  - `io.agentscope.core.a2a.server.executor.runner.ReActAgentWithBuilderRunner`
  - `io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions`
  - `io.agentscope.core.a2a.server.executor.AgentExecuteProperties`
- AG-UI:
  - `io.agentscope.core.agui.adapter.AguiAgentAdapter`
  - `io.agentscope.core.agui.adapter.AguiAdapterConfig`
  - `io.agentscope.core.agui.processor.AguiRequestProcessor`
  - `io.agentscope.core.agui.registry.AguiAgentRegistry`
  - `io.agentscope.core.agui.event.AguiEventType`

## Signature Corrections Worth Remembering

- `ReActAgent.Builder` uses `maxIters(int)`, not `maxIterations(int)`.
- `ReActAgent.Builder` also exposes:
  - `enablePlan()`
  - `planNotebook(...)`
  - `skillBox(...)`
  - `knowledge(...)`
  - `knowledges(...)`
  - `ragMode(...)`
  - `retrieveConfig(...)`
  - `statePersistence(...)`
  - `longTermMemory(...)`
  - `longTermMemoryMode(...)`
  - `toolExecutionContext(...)`
  - `structuredOutputReminder(...)`
- `Toolkit` in core `agentscope` is constructed with constructors such as `new Toolkit()` or `new Toolkit(config)`.
- `Toolkit` does not expose `Toolkit.builder()` in the inspected core artifact.
- `JsonSession` in the inspected core artifact is constructor-based:
  - `new JsonSession()`
  - `new JsonSession(Path)`
- `SessionManager` is a real helper in the core artifact:
  - `SessionManager.forSessionId(...)`
  - `withSession(...)`
  - `addComponent(...)`
  - `loadIfExists()`
  - `saveSession()`
- `StatePersistence` is a record with four managed flags:
  - `memoryManaged`
  - `toolkitManaged`
  - `planNotebookManaged`
  - `statefulToolsManaged`

## Event And Hook Reality Check

- Agent stream events are exactly:
  - `REASONING`
  - `TOOL_RESULT`
  - `HINT`
  - `AGENT_RESULT`
  - `SUMMARY`
  - `ALL`
- Hook event types are exactly:
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

## Classes Not Found In The Inspected Core Artifact

These names are not present in the local `io.agentscope:agentscope:1.0.9` core jar, so do not present them as guaranteed core APIs without re-checking extra modules or newer versions:

- `Toolkit.builder()`
- `JsonSession.builder()`
- `MemoryFactory`
- `RedisMemory`
- `MapDBMemory`
- `DiskMemory`
- `ReWooPlanner`
- `ParallelPlanner`
- `UserQueryUtil`
- `AskUserOptions`
- `RecoveryPoint`
- `StoreStateListener`
- `WorkflowContext`
- `OperatorContext`
- `StatefulOperator`
- `ConversationContextUtil`
- `AgentSkillProperties`
- `CommonAgent`
- `ConsumerAgent`
- `ExecutorAgent`
- `A2AAgentExecutor`
- `A2AAgentSkill`
- `AguiAgentRegistryCustomizer`

That does not necessarily mean the official docs are wrong in every context. It means those names are not exposed by the inspected core artifact, so local code using only the core dependency cannot assume they exist.

## Practical Rule

When official tutorial prose and the local core artifact disagree:

1. use the official page for concepts and examples
2. use the local artifact for class names and method signatures
3. call out the mismatch explicitly in answers
