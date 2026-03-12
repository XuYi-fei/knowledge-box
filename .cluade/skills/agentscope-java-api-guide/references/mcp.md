# MCP

## What It Is

MCP support lets AgentScope Java connect to external tool servers over the Model Context Protocol.

## Official Example

Official stdio client example:

```java
McpToolKit mcpToolKit = McpToolKit.builder()
    .toolkitName("mcpToolkit")
    .mcpClient(
        McpSyncClient.create(
            StdioClientTransport.builder("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", ".")
                .build()
        )
    )
    .build();
```

## Dependency Note

The official docs explicitly say MCP SDK is not always bundled automatically. Add the MCP SDK dependency when you actually use MCP features.

## What The Docs Emphasize

- access external tools through one protocol
- support `StdIO`, `SSE`, and `HTTP` transport
- reuse the MCP ecosystem instead of hand-writing every integration

## Practical Usage Path

1. Add the MCP SDK dependency required by the docs.
2. Configure an MCP client or toolkit.
3. Import MCP tools into AgentScope through `McpToolKit`.
4. Expose them to a `ReActAgent` via `Toolkit`.

## When To Use It

- external tools already exist as MCP servers
- you need standard protocol integration instead of custom adapters
- you want tool capability reuse across different agent runtimes

## Common Pitfalls

- Do not assume MCP works without the extra dependency.
- Transport choice changes operational behavior; `StdIO`, `SSE`, and `HTTP` have different deployment and failure characteristics.
- MCP reduces integration code, but not auth, timeout, or permission design.

## Related Files

- Tool calling: [tool-calling.md](tool-calling.md)
- AG-UI for frontend exposure: [agui.md](agui.md)

## Official Doc

- <https://java.agentscope.io/zh/task/mcp.html>
