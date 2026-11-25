package com.veritaspay.payment;

import com.veritaspay.ledger.LedgerDtos;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    public record TransferRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID destinationAccountId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Size(max = 255) String description,
            boolean merchantPayment
    ) {}

    public record TopUpRequest(
            @NotNull UUID destinationAccountId,
            @NotNull @DecimalMin("1.00") @DecimalMax("500000.00") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Size(max = 255) String description
    ) {}

    public record TransferResult(
            LedgerDtos.JournalTransactionView transaction,
            boolean idempotentReplayed,
            String idempotencyKey,
            BigDecimal feeApplied
    ) {}

    public record PaymentEventPayload(
            UUID transactionId,
            String referenceId,
            String eventType,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            Instant occurredAt
    ) {}
}
