# Message Storage

Feature [#861](https://github.com/karanbabu2110/KAOS/issues/861) and Task
[#1085](https://github.com/karanbabu2110/KAOS/issues/1085) extend the concrete
`SqliteConversationStore` with durable ordered messages. A caller can append a
validated message batch to an existing conversation in one transaction and
reconstruct one bounded `ConversationHistory` after the database is reopened.

This document preserves the Feature 004.03 delivery boundary. Feature 004.05
now invokes the same API from the conversation runtime; see
[Conversation Restore](conversation-restore.md).

## Implemented behavior

- `appendMessages` accepts a positive conversation identifier and a non-empty
  batch of validated `ConversationMessage` values.
- The complete batch is inserted with prepared parameters and committed once.
  If any insert fails, the transaction rolls back every message in that batch.
- SQLite foreign-key enforcement is enabled for the write connection, and the
  parent conversation must already exist.
- `conversationHistory` orders rows by their insertion sequence, maps only the
  supported `USER` and `ASSISTANT` roles, and reconstructs the existing domain
  objects so their content and size invariants are reapplied.
- A stored or loaded conversation remains bounded to
  `ConversationHistory.MAX_MESSAGES` (currently 64). The database collection
  has no eight-conversation or 512-total-message limit.

The API remains a direct concrete class in `io.kaos.conversation`. No repository
interface, module, service, event, worker, or background write path is added.

## Data and safety evidence

Deterministic temporary-database tests prove:

- user and assistant roles plus Unicode, apostrophes, and line breaks survive
  close and reopen exactly;
- separate batches preserve one deterministic insertion order;
- a trigger-induced failure on the second insert leaves zero messages from the
  batch;
- invalid identifiers, null/empty/oversized batches, and null entries fail
  before database access where applicable;
- a missing parent conversation cannot receive messages;
- invalid stored roles and histories beyond the read bound fail without
  exposing database paths, SQL, message content, or driver details; and
- nine conversations can hold 576 messages, proving the persisted collection
  is not capped by the foreground session's eight-conversation/512-message
  aggregate.

Messages are private local user data. Feature 004.03 itself wrote only
test-owned temporary files; Feature 004.05 later selected the production path
and runtime lifecycle. Operating-system account and disk protections remain the
available at-rest protection; no encryption or key-management claim is introduced.

## Schema contract and exclusions

Tests explicitly create minimal `conversations` and `messages` tables. The
message fixture supplies an insertion `sequence`, parent
`conversation_identifier`, `role`, and exact `content`. This demonstrates the
store contract but is not production schema initialization or migration.

Feature 004.04 owns production schema versioning, initialization, indexes, and
migrations. Feature 004.05 owns application wiring and restart restoration.
Feature 004.06 owns complete locked, unreadable, corrupt, disk-full, diagnostic,
and recovery policy. Feature 004.07 owns complete persistence integration.

## Validation

Run the focused store suite with:

```powershell
./gradlew.bat test --tests 'io.kaos.conversation.SqliteConversationStoreTest' --no-daemon --warning-mode=all
```

Before completion, also run the clean `verifyLocal` checkpoint, diff and local
link checks, and desktop/mobile architecture validation. Rollback before
application wiring is removal of the message methods, tests, and this evidence;
no production user database currently exists.
