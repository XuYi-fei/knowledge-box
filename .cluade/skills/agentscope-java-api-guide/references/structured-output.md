# Structured Output

## What The Docs Offer

AgentScope Java exposes structured output through the agent call surface plus internal structured-output hooks.

## Official Example

Official example from the docs:

```java
StructuredOutputResult<WeatherResponse> result = chatModel
    .withStructuredOutput(WeatherResponse.class, JsonSchemaMode.STRICT)
    .chat(ChatMessage.ofUser("北京今天天气怎么样？"));
```

## Source-Verified Core Surface

The inspected core artifact exposes:

- `StructuredOutputCapableAgent`
- `StructuredOutputHook`
- `StructuredOutputReminder`
- `ResponseFormat`

`StructuredOutputReminder` in the core jar has two modes:

- `TOOL_CHOICE`
- `PROMPT`

`ResponseFormat` in the core jar supports:

- `text()`
- `jsonObject()`
- `jsonSchema(...)`

## Core Mental Model

- `ReActAgent` inherits structured-output capability because it extends `StructuredOutputCapableAgent`.
- Structured output is triggered from agent calls that target:
  - a POJO class
  - a JSON schema node
- `StructuredOutputHook` is the internal helper that aggregates result message, usage, and thinking during structured generation.

## How To Choose A Mode

- `StructuredOutputReminder.PROMPT`: rely more on prompt-level reminders.
- `StructuredOutputReminder.TOOL_CHOICE`: rely more on tool-choice-style structured prompting.
- `ResponseFormat.jsonSchema(...)`: use when the underlying formatter/provider supports schema mode.

## Practical Guidance

- Start from the target schema or POJO, then choose the mode.
- Use structured output for machine-consumed results, not for general prose.
- Keep schemas narrow and explicit. Overly broad schemas reduce correctness.
- Add validation and fallback handling even when the provider claims strict compliance.

## Common Pitfalls

- Not every model supports every mode equally well.
- Strict mode is provider-sensitive; treat it as a capability check, not a universal default.
- Tool-calling mode still depends on good tool or schema descriptions.
- Do not expect one schema to fit both human-facing narrative and machine-facing contracts.
- Do not assume tutorial types like `JsonSchemaMode` are part of the core artifact unless you verified the module.

## Related Files

- Tool calling: [tool-calling.md](tool-calling.md)
- Agent skill response contracts: [agent-skill.md](agent-skill.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/structured-output/>
