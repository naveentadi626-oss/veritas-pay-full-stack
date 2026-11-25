package com.veritaspay.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LedgerDtos {

    public record LedgerEntryView(
            UUID id,
            UUID accountId,
            String accountNumber,
            String accountName,
            EntryType entryType,
            BigDecimal amount,
            String currency,
            BigDecimal balanceAfter,
            Instant createdAt
    ) {}

    public record JournalTransactionView(
            UUID id,
            String referenceId,
            String idempotencyKey,
            TransactionType transactionType,
            TransactionStatus status,
            String currency,
            BigDecimal totalAmount,
            String description,
            String correlationId,
            Instant createdAt,
            List<LedgerEntryView> entries
    ) {}

    public record TrialBalanceReport(
            boolean balanced,
            BigDecimal totalDebits,
            BigDecimal totalCredits,
            BigDecimal discrepancy,
            long totalEntries,
            long totalTransactions,
            Instant verifiedAt
    ) {}
}
