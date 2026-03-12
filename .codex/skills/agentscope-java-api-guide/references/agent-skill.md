# Agent Skill

## What It Is

`AgentSkill` packages a capability behind a stable contract so other agents or systems can invoke it like a reusable unit.

## Official Example

The official page also shows skill package loading:

```python
skill = load_skill_through_path("./weather")
print(skill.skill_name)
```

The same page shows a typical skill package layout:

```text
weather/
├── SKILL.yaml
└── skill.py
```

## Source-Verified Core Construction Pattern

The inspected core artifact exposes a different surface from the tutorial-style `AgentSkillProperties` API. The actual core builder is:

```java
AgentSkill skill = AgentSkill.builder()
    .name("weather_query")
    .description("Get the weather information for a given city.")
    .skillContent("Use this skill to answer weather questions.")
    .addResource("examples/README.md", "...")
    .source("filesystem")
    .build();
```

## Core APIs

- `AgentSkill.builder()`: skill entry point.
- `name`: display name.
- `description`: routing hint for callers and registries.
- `skillContent`: primary markdown or text body
- `resources`: additional bundled files
- `source`: where the skill came from
- `SkillBox`: in-process registry and exposure surface
- `SkillHook`: hook that injects skill behavior into the agent
- `AgentSkillRepository`: repository abstraction for loading and saving skills

The following names are not present in the inspected core jar:

- `AgentSkillProperties`
- `starter(...)`

## Spring Boot Integration

The docs also provide `agentscope-skill-spring-boot-starter`. In Spring Boot, expose the skill as a bean instead of hand-wiring everything in plain Java.

## Source-Verified Runtime Notes

- `SkillBox` is both a runtime registry and a `StateModule`.
- `SkillBox` can:
  - `registerSkill(...)`
  - `removeSkill(...)`
  - `deactivateAllSkills()`
  - `registerSkillLoadTool()`
  - `bindToolkit(...)`
  - `uploadSkillFiles()`
- `SkillHook` is a normal `Hook` implementation and can be added to agents.

## When To Use It

- Encapsulating a sub-agent behind a stable API
- Exposing business capabilities to other agents
- Turning a workflow into a reusable, documented capability

## Common Pitfalls

- Keep `skillName` stable. Treat it like an API id, not a marketing label.
- `description` should describe capability and input, not just repeat the name.
- `inputSchema` should be minimal but strict enough to reject ambiguous calls.
- Do not hide heavy side effects behind a skill without making the contract explicit.
- If the skill internally calls tools or agents, surface timeout and error behavior clearly to callers.

## Related Files

- ReAct agent reuse: [react-agent.md](react-agent.md)
- Structured outputs for skill response contracts: [structured-output.md](structured-output.md)
- A2A exposure: [a2a.md](a2a.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/agent-skill/>
