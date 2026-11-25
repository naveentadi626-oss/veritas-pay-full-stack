# VeritasPay — Enterprise Double-Entry FinTech Payment & Ledger Platform

> **Industry-Standard Java 21 + Spring Boot 3.3 + React 18 TypeScript Full-Stack Flagship Application**

VeritasPay demonstrates production FinTech engineering: **ACID double-entry bookkeeping**, **Idempotency-Key deduplication**, **deadlock-free pessimistic lock ordering**, **Transactional Outbox over Apache Kafka**, **JWT RBAC + refresh token rotation**, **Redis rate limiting**, **Prometheus + Grafana observability**, **Testcontainers integration suites**, and **Kubernetes deployment manifests**.

---

## Architecture Highlights

- **Double-Entry Bookkeeping (`DoubleEntryValidator` & `LedgerService`)**: Every top-up, P2P transfer, and merchant payment (with automatic 1% platform fee split) writes balanced `DEBIT` and `CREDIT` lines using `BigDecimal(19, 4)`. System-wide trial balance (`/api/v1/ledger/reconciliation/trial-balance`) verifies `SUM(DEBIT) == SUM(CREDIT)`.
- **Idempotency & Concurrency Control**:
  - `Idempotency-Key` header + SHA-256 payload fingerprinting replays exact retries (`200 OK`, `X-Idempotent-Replayed: true`) and rejects parameter tampering (`409 Conflict`).
  - Deterministic ascending UUID row locking (`SELECT ... FOR UPDATE ORDER BY id ASC`) eliminates circular-wait deadlocks under opposing concurrent transfers.
- **Transactional Outbox & Kafka Settlement**:
  - `OutboxEvent` rows commit atomically with ledger entries, relayed by `OutboxRelayScheduler` to `veritas.payment.events`, and consumed by `PaymentEventConsumer` to transition `POSTED -> SETTLED`.
- **Enterprise Security & Observability**:
  - Spring Security 6 stateless JWT + BCrypt(12) + Refresh Token rotation + RBAC (`CUSTOMER`, `MERCHANT`, `COMPLIANCE_OFFICER`, `ADMIN`).
  - RFC 7807 `ProblemDetail` error responses with `X-Correlation-Id` MDC propagation.
  - Redis fixed-window rate limiter (`RateLimitFilter`).
  - Micrometer custom counters + Prometheus + pre-provisioned Grafana dashboard.

---

## Quick Start (Full Stack with Docker Compose)

```bash
cp .env.example .env
docker compose up --build
```

Once healthy:
- **Frontend Console**: http://localhost:3000 (1-click demo personas on login screen!)
- **Backend Swagger / OpenAPI**: http://localhost:8080/swagger-ui.html
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (`admin` / `admin`)

---

## Testing

### Backend Unit & Testcontainers Integration Tests
```bash
cd backend
./mvnw test        # Unit tests (DoubleEntryValidator, LedgerAccount, JwtService)
./mvnw verify      # Full suite including Testcontainers (Postgres 16 + Kafka + Redis)
```

### Frontend Typecheck, Vitest & Playwright E2E
```bash
cd frontend
npm install
npm run typecheck
npm test -- --run
npx playwright test
```

---

## Documentation & Interview Prep

- [System Architecture & Sequence Diagrams](docs/ARCHITECTURE.md)
- [ADR 001: Double-Entry Ledger Model](docs/adr/001-double-entry-ledger.md)
- [ADR 002: Idempotency & Deadlock-Free Locking](docs/adr/002-idempotency-and-locking.md)
- [ADR 003: Transactional Outbox Pattern](docs/adr/003-transactional-outbox.md)
- [MNC Hiring Manager Portfolio & Interview Guide](docs/PORTFOLIO_INTERVIEW_GUIDE.md)
