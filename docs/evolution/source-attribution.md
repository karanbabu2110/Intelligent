# Source Attribution

Feature [005.08](https://github.com/karanbabu2110/KAOS/issues/872) gives every
evidence record included in a grounded prompt one stable, inspectable source
reference.

## Implemented outcome

`GroundedPromptBuilder` labels included evidence `[1]`, `[2]`, or `[3]` in
ranked order and instructs a future answering model to use those labels for
supported claims. `GroundedPrompt.citations()` derives the matching immutable
`SourceCitation` values from the exact included contexts, so omitted records
cannot appear in the citation manifest.

Each citation contains only its label number, stored-document identifier,
source name, and chunk index. `knowledge-retrieve` prints those coordinates
after the prompt summary without printing the query, evidence content, vectors,
model name, or prompt.

## Boundaries and limitations

- Labels are local to one prompt and follow its one-to-three included records.
- Source names are the bounded names already stored during document ingestion;
  citations do not reveal document paths or content.
- Citation construction is deterministic and in process. It adds no database,
  framework, provider request, or network boundary.

No grounded answer is generated yet, so KAOS does not claim that a model used a
label correctly or that any answer sentence is supported. Feature 005.09 owns
RAG evaluation and testing; answer generation and post-generation citation
validation remain deferred until an explicit roadmap boundary supplies them.

## Verification

```powershell
./gradlew.bat test --tests "io.kaos.knowledge.GroundedPromptBuilderTest" --tests "io.kaos.knowledge.SourceCitationTest" --tests "io.kaos.app.KnowledgeRetrieveCommandTest" --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```
