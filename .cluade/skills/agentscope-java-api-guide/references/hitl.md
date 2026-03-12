# Human In The Loop

## Core API

The official tutorial uses ask-user style helpers such as `UserQueryUtil.askUser(...)`:

```java
String result = UserQueryUtil.askUser(
    "What is your preferred mode of transportation?",
    AskUserOptions.builder()
        .options(TransportationMode.values())
        .build()
);
```

The same page also shows free-text mode:

```java
String result = UserQueryUtil.askUser(
    "What is your name?",
    AskUserOptions.builder()
        .build()
);
```

## What It Solves

- approval before a high-risk action
- missing user input mid-workflow
- branching on business confirmation
- controlled pause and resume in long-running tasks

## Practical Usage Pattern

1. Reach a decision point inside an agent or workflow.
2. Use a user-input bridge to collect the next input.
3. Constrain expected input as much as possible.
4. Persist enough state to resume the run after the user responds.

## Source-Verified Core Surface

The inspected core artifact does not expose `UserQueryUtil` or `AskUserOptions`.

What it does expose is:

- `UserAgent`
- `UserInputBase`
- `StreamUserInput`
- `InterruptContext`
- `ToolSuspendException`

This means the core artifact clearly supports:

- agent-side user input collection
- interrupt metadata capture
- tool-driven suspension

But the exact helper names shown in the tutorial are not part of the inspected core jar.

## Core-Level Mental Model

- `UserAgent` is the concrete agent for collecting input.
- `StreamUserInput` is the built-in stream-based input implementation.
- `InterruptContext` carries:
  - interrupt source
  - timestamp
  - user message
  - pending tool calls
- `ToolSuspendException` is the low-level suspension signal when tool execution must pause.

## Common Pitfalls

- HITL is not just a blocking console prompt. In production it should be paired with continuation and task resumption.
- Prefer bounded choices over free-form input when the downstream path is sensitive.
- Persist the task identity and resume path; otherwise ask-user points become dead ends after restarts.
- Define timeout or fallback behavior for unanswered prompts.
- Do not promise `UserQueryUtil` as a core API unless you verified the exact module your project imports.

## Related Files

- Continuation and recovery: [state-session.md](state-session.md)
- Hooks: [hooks.md](hooks.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/human-in-the-loop/>
