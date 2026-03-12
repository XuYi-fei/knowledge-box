# Tool Calling

## Core Entry Points

The current official docs expose tools mainly through `@Tool`, `@ToolParam`, `Toolkit`, and direct `AgentTool` implementations. The older `@ToolFunction` style should not be treated as the primary entry for current Java docs.

## Define a Tool

Use annotations such as:

```java
public class WeatherToolService {

    @Tool(name = "searchWeather", description = "根据城市名称查询天气")
    public String searchWeather(
        @ToolParam(name = "cityName", description = "城市名称") String cityName
    ) {
        return "深圳，多云";
    }
}
```

Official registration example:

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new WeatherToolService());
```

## Important Tool APIs

- `@Tool`: expose a method as a callable tool.
- `@ToolParam`: document tool arguments for the model.
- `Toolkit`: register and organize tools.
- `ToolGroup`: group related tools and reduce selection noise.
- `ToolExecutionContext`: pass runtime context objects into tool calls.
- `ToolCallParam`: low-level tool invocation payload.
- `McpTool`: wrap an MCP-exposed tool as an `AgentTool`.
- `SubAgentTool`: expose a sub-agent as a tool.
- `AgentTool`: low-level interface when you need custom schema or async control.

## Source-Verified Core Surface

From the inspected core artifact:

- `Toolkit` is constructor-based in the core jar:
  - `new Toolkit()`
  - `new Toolkit(config)`
- `Toolkit` exposes:
  - `registerTool(Object)`
  - `registerAgentTool(AgentTool)`
  - `registerSchema(...)`
  - `callTool(...)`
  - `callTools(...)`
  - `createToolGroup(...)`
  - `registerMcpClient(...)`
  - `updateToolPresetParameters(...)`
- `AgentTool` has four required methods:
  - `getName()`
  - `getDescription()`
  - `getParameters()`
  - `callAsync(ToolCallParam)`
- `ToolExecutionContext.builder()` and `ToolCallParam.builder()` both exist in the core jar.

The following names are not present in the inspected core jar, so do not treat them as guaranteed core APIs without re-checking extra modules:

- `Toolkit.builder()`
- `@PresetToolArg`
- `@ToolUseTime`
- `@ToolMemory`
- `ToolContext`

## How To Use It Well

- Put tools behind clear names and strong descriptions.
- Keep parameters narrow and typed.
- Always set `@ToolParam(name = "...")`. The official docs call out that Java does not preserve parameter names by default.
- Group tools by domain when the agent has many capabilities.
- Use preset args for tenant id, auth scope, or other server-controlled values.
- Prefer MCP when a capability already exists as a standard tool server.

## Parallel Tool Calls

Parallel calls need two layers to cooperate:

- the agent must allow parallel tool calling
- the model must be able to plan multiple calls in one turn

Only enable this when tools are independent and safe to run concurrently.

## Common Pitfalls

- Avoid vague tool names like `run` or `process`.
- Avoid high-side-effect tools unless confirmation or HITL is in place.
- MCP tools still need timeout, auth, and availability handling.
- If you need reactive or nontrivial result handling, implement `AgentTool` directly instead of forcing everything into annotation methods.
- If you need exact signatures, also read [source-code-map.md](source-code-map.md).

## Related Files

- ReAct agent: [react-agent.md](react-agent.md)
- Human in the loop: [hitl.md](hitl.md)
- Structured output: [structured-output.md](structured-output.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/builtin-tool/>
- <https://java.agentscope.io/zh/task/tool.html>
