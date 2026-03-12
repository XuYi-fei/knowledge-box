---
name: knowledge-box-architecture-maintainer
description: 维护 Knowledge Box 仓库的 architecture.md。适用于需要理解当前项目结构、前后端交互、后端配置、代码组织、请求地址、LLM 调用链，或希望在代码变更后刷新 architecture.md 以便调试和维护的场景。
---

# Knowledge Box 架构维护器

使用这个 skill 的目标，是让 `architecture.md` 始终与当前代码库保持一致，帮助用户理解 AI 编写的代码，并能进行细粒度调试和维护。

始终把 `architecture.md` 视为持续维护的产物，而不是一次性的概览文档。

## 必读内容

1. 阅读 `../../../rule.md`。
2. 阅读 `../../../progress.md`。
3. 如果存在 `../../../architecture.md`，先阅读；如果不存在，则创建。
4. 只阅读本次变更相关的项目文件，优先从 [references/scan-map.md](references/scan-map.md) 开始。

## 核心流程

1. 阅读当前 `architecture.md`，理解上一次的架构叙述。
2. 扫描当前代码库，识别以下方面发生了哪些变化：
   - 前端整体设计
   - 前后端交互逻辑
   - 前端请求基址与接口配置位置
   - 后端整体设计
   - 后端配置面与配置项含义
   - 面向前端的后端入口
   - LLM / RAG 入口与核心类
   - 核心功能链路及其主类
   - 项目级整体结构
3. 按 [references/architecture-template.md](references/architecture-template.md) 中的结构，原地更新 `../../../architecture.md`。
4. 在 `architecture.md` 的“变更记录”部分追加一条简短、带日期的说明，写清这次文档更新了什么。
5. 如果 `architecture.md` 或这个维护 skill 发生了有意义的变化，同步更新 `../../../progress.md`。

## 内容规则

- 面向需要调试和扩展代码的维护者来写，不要写成宣传文案。
- 优先给出具体文件路径、类名、路由前缀和配置键。
- 先解释真实活跃的入口；如果存在遗留目录或空目录，要明确标注。
- 说明配置项含义，但绝不复制 `application-local.yml` 中的用户私有值或敏感值。
- `architecture.md` 要保持可读，但也要足够详细，能支撑排查主要请求链路。
- 某个主题变了，就重写对应段落，不要通过追加方式留下互相矛盾的描述。
- `architecture.md` 的所有正文内容默认使用中文；只有代码标识符、类名、配置键、接口路径等技术名词保留原文。

## `architecture.md` 必须覆盖的内容

- 前端整体设计
- 前后端交互流程
- 前端请求基址与接口配置位置
- 前端代码组织与维护者需要知道的说明
- 后端整体设计
- 后端配置分组、含义以及绑定位置
- 面向前端 API 的后端入口
- 大模型调用入口与核心类
- 核心功能链路、入口与主类
- 项目整体架构
- 特殊修改、注意事项和维护提示

## 输出要求

- 章节标题尽量保持稳定，方便后续做 diff。
- 如果代码位置迁移了，而旧路径仍可能让维护者混淆，要同时说明新旧路径。
- 如果这次没有运行验证，要在 `progress.md` 中明确写出；架构文档更新通常以代码阅读为主。
