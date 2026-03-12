# Multimodal

## What The Docs Cover

The multimodal guide is built around the unified `ContentBlock` hierarchy.

## Official Example

Official message-block example:

```java
Message message = Message.builder()
    .role(Role.USER)
    .content(
        TextBlock.builder()
            .text("请描述这张图片")
            .build()
    )
    .content(
        ImageBlock.builder()
            .imageUri("https://example.com/cat.png")
            .build()
    )
    .build();
```

## Core Content Blocks

- `TextBlock`
- `ImageBlock`
- `AudioBlock`
- `VideoBlock`
- `ThinkingBlock`
- `ToolUseBlock`
- `ToolResultBlock`

## Why It Matters

AgentScope uses one message model for text and media. That keeps agent code more stable when you switch from plain chat to image, audio, or video inputs.

## Practical Guidance

- Keep content representation explicit with the right block type.
- Check whether your chosen model provider actually supports the media type you pass in.
- For media-heavy workflows, combine multimodal support with structured output or tools only when the provider handles both reliably.

## Common Pitfalls

- A model class supporting vision does not imply full support for every audio or video path.
- Large media payloads can dominate latency and cost.
- Mixed content is powerful, but debugging becomes harder if you do not log block types clearly.

## Related Files

- Model capabilities: [model.md](model.md)
- Tool-based multimodal operations: [tool-calling.md](tool-calling.md)

## Official Doc

- <https://java.agentscope.io/zh/task/multimodal.html>
