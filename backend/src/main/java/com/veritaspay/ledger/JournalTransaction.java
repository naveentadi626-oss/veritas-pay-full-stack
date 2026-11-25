package com.veritaspay.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_transactions")
public class JournalTransaction {

    @Id
    private UUID id;

    @Column(name = "reference_id", nullable = false, unique = true, length = 64)
    private String referenceId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(length = 255)
    private String description;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JournalTransaction() {}

    public JournalTransaction(UUID id, String referenceId, String idempotencyKey,
                              TransactionType transactionType, TransactionStatus status,
                              String currency, BigDecimal totalAmount, String description,
                              UUID initiatedBy, String correlationId) {
        this.id = id;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
        this.transactionType = transactionType;
        this.status = status;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    public void markSettled() {
        if (this.status == TransactionStatus.POSTED) {
            this.status = TransactionStatus.SETTLED;
        }
    }

    public void markReversed() {
        this.status = TransactionStatus.REVERSED;
    }

    public UUID getId() { return id; }
    public String getReferenceId() { return referenceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransactionType getTransactionType() { return transactionType; }
    public TransactionStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getDescription() { return description; }
    public UUID getInitiatedBy() { return initiatedBy; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}
