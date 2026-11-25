# ADR 001: Double-Entry Bookkeeping Ledger Model

## Status
Accepted

## Context
Naive payment systems store a single mutable `balance` column and perform `UPDATE accounts SET balance = balance - ?`. Under that model, bugs, partial failures, or silent rounding errors destroy auditability—there is no mathematical proof that funds were neither created nor destroyed.

## Decision
VeritasPay models every financial state change as an immutable `journal_transactions` header containing 2 or more `ledger_entries` (`DEBIT` or `CREDIT`).
- Every account declares an explicit `normal_balance` (`DEBIT` for `SYSTEM_TREASURY`; `CREDIT` for `USER_WALLET`, `MERCHANT_SETTLEMENT`, `FEE_REVENUE`).
- `DoubleEntryValidator` enforces `SUM(DEBIT) == SUM(CREDIT)` using `BigDecimal` (scale 4, `HALF_EVEN`) before any database write.
- `ledger_entries` are append-only (`balance_after` snapshots provide O(1) point-in-time balance history).

## Consequences
- System-wide reconciliation (`/api/v1/ledger/reconciliation/trial-balance`) can prove mathematical integrity at any instant in O(1) aggregate SQL.
- Slightly higher write amplification (2-3 `ledger_entries` per transfer), which is well worth the auditability in FinTech systems.
