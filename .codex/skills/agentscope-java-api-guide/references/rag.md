# RAG

## Core Concept

The official tutorial presents AgentScope Java RAG around knowledge objects, embeddings, vector storage, and agent integration. The inspected core artifact confirms that the core abstraction is `Knowledge`.

## Official Example

Official example from the docs:

```java
SimpleKnowledge knowledge = SimpleKnowledge.builder()
    .id("example-knowledge")
    .embeddingModel(embeddingModel)
    .vectorStore(vectorStore)
    .build();

knowledge.addObjects(
    Arrays.asList(
        KnowledgeObject.fromFile("src/main/resources/doc/README.md")
    )
);
```

## Source-Verified Core APIs

- `Knowledge`: the main abstraction
- `SimpleKnowledge`: built-in local knowledge implementation
- `GenericRAGHook`: hook-based generic retrieval integration
- `KnowledgeRetrievalTools`: tool wrapper around knowledge retrieval
- `RAGMode`: `GENERIC`, `AGENTIC`, `NONE`
- `RetrieveConfig`: retrieval config object

`SimpleKnowledge.Builder` in the core artifact exposes:

- `embeddingModel(...)`
- `embeddingStore(...)`

## Typical Usage Path

1. Create or configure a knowledge base.
2. Ingest documents with `addDocuments(...)`.
3. Keep chunking, metadata, and embeddings consistent.
4. Expose retrieval to an agent through `knowledge(...)`, `ragMode(...)`, `retrieveConfig(...)`, a hook, or a tool.

## Source-Verified Builder Integration

`ReActAgent.Builder` in the inspected core jar directly supports:

- `knowledge(...)`
- `knowledges(...)`
- `ragMode(...)`
- `retrieveConfig(...)`

## When To Use It

- Retrieval over project docs, SOPs, or product knowledge
- Hybrid agent flows where tool use and knowledge retrieval coexist
- Cases where prompt stuffing is too large or too unstable

## Common Pitfalls

- Do not mix embedding models between indexing and retrieval unless you know the compatibility story.
- Chunking quality matters more than people expect. Oversized chunks often reduce recall.
- Keep source metadata so answers can cite origin and support traceability.
- Decide early whether the store must be persistent, multi-tenant, or local-only.
- RAG is not a substitute for tool execution. Use tools for live data and actions.
- Do not copy `addObjects(...)` from tutorial prose into core-only code without checking the local artifact; the inspected core surface is `addDocuments(...)`.

## Related Files

- Tool calling: [tool-calling.md](tool-calling.md)
- Memory vs knowledge separation: [memory.md](memory.md)

## Official Doc

- <https://java.agentscope.io/zh/tutorial/rag/>
