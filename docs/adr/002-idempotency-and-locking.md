# ADR 002: Idempotency Keys & Deadlock-Free Concurrency Control

## Status
Accepted

## Context
Payment APIs experience client timeouts and automatic network retries. Executing a transfer twice causes double charges. Simultaneously, concurrent opposing transfers (`A -> B` and `B -> A`) acquiring row locks in arbitrary order can deadlock PostgreSQL.

## Decision
1. **Idempotency-Key + Request Fingerprinting**: Every `POST /api/v1/payments/transfers` requires an `Idempotency-Key` header. The server computes a SHA-256 hash of `(source, destination, normalizedAmount, currency, merchantPayment)` and persists an `idempotency_records` row (`IN_PROGRESS` -> `COMPLETED`).
   - Exact retries return `200 OK` with `X-Idempotent-Replayed: true` and zero balance mutation.
   - Reusing a key with modified parameters fails fast with `409 Conflict` (`IDEMPOTENCY_PAYLOAD_MISMATCH`).
2. **Deterministic Lock Ordering + Optimistic Versioning**: Before mutating balances, `LedgerService` sorts involved `account_id` UUIDs ascending and locks rows with `SELECT ... FOR UPDATE ORDER BY id ASC`, eliminating circular wait deadlocks, backed by `@Version` optimistic locking.

## Consequences
- Zero double-charges on mobile/gateway retries.
- Zero database deadlocks under opposing concurrent transfer load.
