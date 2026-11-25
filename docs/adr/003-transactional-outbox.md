# ADR 003: Transactional Outbox Pattern for Kafka Event Streaming

## Status
Accepted

## Context
Publishing to Kafka directly inside a `@Transactional` DB method introduces the dual-write problem: if DB commits and Kafka fails (or Kafka publishes and DB rolls back), downstream settlement services drift out of sync with the ledger.

## Decision
`PaymentTransferService` writes `outbox_events` (`status = PENDING`) inside the **exact same ACID PostgreSQL transaction** as `journal_transactions` and `ledger_entries`. `OutboxRelayScheduler` polls pending rows and publishes to `veritas.payment.events`, marking them `PUBLISHED` upon broker ACK. `PaymentEventConsumer` consumes the event idempotently to transition transactions `POSTED` -> `SETTLED`.

## Consequences
- Guaranteed at-least-once event delivery without distributed 2PC transactions.
- Downstream consumers must remain idempotent (`markSettled()` checks current state).
