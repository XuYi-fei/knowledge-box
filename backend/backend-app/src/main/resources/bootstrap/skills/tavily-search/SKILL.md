---
name: tavily-search
description: Guide the agent to perform grounded web search, cite sources, and explain clearly when no reliable result is found.
---

# Tavily Search Skill

Use this skill when the user asks for public, external, or likely up-to-date information that is not guaranteed to exist in the local knowledge base.

## Required behavior

1. Call `searchWeb` before drafting the final answer whenever external search is needed.
2. Base the answer strictly on the returned search evidence.
3. Explicitly cite the returned source URLs in the final answer.
4. If the search returns no reliable result, state that clearly instead of guessing.
5. If the search tool reports provider fallback or temporary unavailability, mention that limitation briefly in the final answer.

## Output style

- Keep the final answer concise and grounded.
- Prefer bullet points when summarizing multiple sources.
- Never fabricate a source or claim that is not supported by the search result payload.
