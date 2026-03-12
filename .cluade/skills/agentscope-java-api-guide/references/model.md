# Model

## What The Official Docs Cover

The model guide focuses on provider-specific chat model classes plus formatter selection.

## Official Example

Official OpenAI-compatible model example:

```java
OpenAIChatModel model = OpenAIChatModel.builder()
    .baseUrl("https://api.openai.com/v1")
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4.1-mini")
    .build();
```

## Supported Model Classes

- `DashScopeChatModel`
- `OpenAIChatModel`
- `AnthropicChatModel`
- `GeminiChatModel`
- `OllamaChatModel`

## Practical Selection Notes

- `OpenAIChatModel` follows the OpenAI API shape, so the docs explicitly note it can also work with compatible providers such as `vLLM` or `DeepSeek`.
- `GeminiChatModel` can target either Gemini API or Vertex AI.
- Not every provider has the same support level for strict schema, reasoning visibility, or multimodal capabilities. Match model choice to the feature you need.

## Formatter Choice

The docs split formatter usage into:

- single-agent formatter
- multi-agent formatter

In multi-agent scenarios such as Pipeline or MsgHub, prefer the provider's `MultiAgentFormatter`. The docs call out that it merges multi-agent history and structures it for the target provider.

## Common Pitfalls

- Do not assume "supports tools" means "supports every structured-output mode well."
- Formatter mismatch is a common cause of degraded multi-agent behavior.
- If you swap providers, re-check streaming, vision, and reasoning support before blaming agent logic.

## Related Files

- ReAct agent: [react-agent.md](react-agent.md)
- Structured output: [structured-output.md](structured-output.md)
- Multimodal: [multimodal.md](multimodal.md)
- Pipeline: [pipeline.md](pipeline.md)

## Official Doc

- <https://java.agentscope.io/zh/task/model.html>
