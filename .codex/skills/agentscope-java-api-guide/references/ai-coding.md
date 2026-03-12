# AI Coding Docs Integration

## What It Is

The official docs provide `llms.txt` and `llms-full.txt` so AI coding tools can load the AgentScope Java documentation set efficiently.

## Official Endpoints

- `https://java.agentscope.io/llms.txt`
- `https://java.agentscope.io/llms-full.txt`

## When To Use It

- configuring AI IDE doc ingestion
- giving coding agents a compact index of AgentScope docs
- reducing manual page hunting during implementation

## Practical Guidance

- prefer `llms.txt` when your tool supports staged retrieval
- use `llms-full.txt` when the client expects a single merged doc
- still verify feature details against the exact page when API behavior is sensitive

## Common Pitfalls

- Doc ingestion is not runtime integration. It only helps coding assistants understand the framework.
- `llms-full.txt` is convenient but heavier; use it only when your tool really benefits from a monolithic doc file.

## Official Doc

- <https://java.agentscope.io/zh/task/ai-coding.html>
