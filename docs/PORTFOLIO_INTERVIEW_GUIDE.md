# Portfolio & MNC Interview Guide — How to Pitch VeritasPay

This guide prepares you to present **VeritasPay** to Hiring Managers, Staff Engineers, and Senior Engineering Managers at top MNCs (JPMorgan, Goldman Sachs, Amazon, Stripe, PayPal, Visa, Walmart Global Tech).

## 1. Resume Bullet Points (Copy-Paste Ready)

- **Architected VeritasPay**, an enterprise Java 21 / Spring Boot 3.3 double-entry payment & ledger platform processing ACID multi-leg transfers (`BigDecimal`, deterministic `SELECT ... FOR UPDATE` lock ordering) with zero balance drift verified by automated trial-balance reconciliation.
- **Eliminated duplicate payment risk** on client retries by implementing RFC-style `Idempotency-Key` deduplication with SHA-256 request payload fingerprinting, returning cached `200 OK` replays while rejecting tampered payloads with `409 Conflict`.
- **Engineered reliable event-driven settlement** using the Transactional Outbox pattern (`outbox_events` -> scheduled Kafka relay -> idempotent settlement consumer), avoiding dual-write inconsistencies without distributed 2PC.
- **Built production observability & DevSecOps pipelines**: JWT access/refresh token rotation + RBAC, Redis sliding-window rate limiting, RFC 7807 `ProblemDetail` errors, OpenTelemetry/Prometheus/Grafana metrics, Testcontainers (`PostgreSQL` + `Kafka` + `Redis`), Docker Compose, Kubernetes HPA manifests, and GitHub Actions CI with Trivy scanning.

## 2. Top 6 Senior/Hiring Manager Follow-Up Questions & How to Answer

1. **"Why did you sort account UUIDs before `SELECT ... FOR UPDATE`?"**
   - *Answer*: If Thread A transfers `Acc1 -> Acc2` while Thread B transfers `Acc2 -> Acc1`, locking in request order causes a classic circular-wait deadlock. Ordering lock acquisition deterministically by ascending `UUID` breaks the cycle so deadlocks cannot occur, and `@Version` provides defense-in-depth optimistic locking.
2. **"Why use a Transactional Outbox instead of calling `kafkaTemplate.send()` inside the `@Transactional` method?"**
   - *Answer*: Calling Kafka inside a DB transaction creates the dual-write problem—either the DB commits and Kafka fails (lost event), or Kafka publishes and the DB rolls back (phantom event). Persisting to `outbox_events` in the same ACID transaction guarantees atomic durability; the relay guarantees at-least-once delivery, and the consumer handles settlement idempotently.
3. **"How do you prevent double charges if a mobile client retries a timed-out request?"**
   - *Answer*: Clients pass an `Idempotency-Key` header. We hash the canonical request body with SHA-256 and store an `idempotency_records` row. Exact retries return the cached response with `X-Idempotent-Replayed: true`; reusing the key with different amount/accounts throws `409 Conflict`.
4. **"Why `BigDecimal` with scale 4 and `normal_balance` instead of `double`?"**
   - *Answer*: IEEE-754 floating-point introduces binary rounding errors (`0.1 + 0.2 != 0.3`). Financial ledgers use fixed-point `NUMERIC(19,4)` / `BigDecimal` with banker's rounding (`HALF_EVEN`), and `normal_balance` (`DEBIT` vs `CREDIT`) lets asset/treasury accounts and liability/wallet accounts obey true accounting rules so `SUM(DEBIT) == SUM(CREDIT)` always holds.
5. **"What would you change to scale this to 50,000 TPS?"**
   - *Answer*: (a) Shard ledger accounts by `account_id` or tenant across PostgreSQL Citus/CockroachDB, (b) replace polling outbox with Debezium CDC reading Postgres WAL logs, (c) batch/aggregate hot system accounts (like `FEE_REVENUE`) into sub-buckets to avoid single-row lock contention.
6. **"What are the next 2 projects to pair with VeritasPay for a complete MNC portfolio?"**
   - **Project 2**: *Multi-Tenant SaaS Work & Approval Engine* (Spring Boot + Row-Level Security / Tenant Isolation + WebSockets + State Machine + Angular/React).
   - **Project 3**: *High-Concurrency Flash-Sale Inventory & Order Saga Platform* (Redis Lua distributed locks / stock reservation + Choreography Saga over Kafka + Dead-Letter Queues + Load testing with k6).
