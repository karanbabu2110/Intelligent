# Relevant-Context Retrieval

Feature [005.06](https://github.com/karanbabu2110/KAOS/issues/870) adds the first
bounded semantic retrieval path over locally stored document chunks.

## Implemented outcome

`knowledge-retrieve "<query>"` validates one exact non-blank query of at most
1,000 Unicode code points and embeds it with the explicit
`KAOS_OLLAMA_EMBEDDING_MODEL`. KAOS loads the bounded local knowledge collection,
keeps only chunks created by the same model with matching vector dimensions,
and calculates cosine similarity directly in process.

Results are ordered by descending similarity, then document identifier and chunk
index for deterministic ties. At most three immutable `RetrievedContext` values
are returned. Each retains the exact chunk for grounded prompt construction,
while the CLI prints only document identifier, safe source name, chunk
index, and six-decimal score. Query text, chunk content, model names, and vectors
are never printed.

## Bounds and failures

- Query: 1–1,000 Unicode code points with unsafe controls rejected.
- Searchable collection: at most 100 documents and 2,000 total chunks.
- Results: at most three; incompatible models, dimensions, and zero vectors are
  skipped.
- Empty compatible context, invalid input, embedding failures, and storage
  failures return content-free recovery guidance.

There is no approximate-nearest-neighbor index, token-budget selection, metadata
filter, threshold configuration, reranking, answer generation, or citation.
Feature 005.07 now constructs the bounded grounded prompt.

## Verification

```powershell
./gradlew.bat test --tests "io.kaos.knowledge.KnowledgeQueryTest" --tests "io.kaos.knowledge.RelevantContextRetrieverTest" --tests "io.kaos.app.KnowledgeRetrieveCommandTest" --tests "io.kaos.knowledge.SqliteKnowledgeStoreTest" --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```
