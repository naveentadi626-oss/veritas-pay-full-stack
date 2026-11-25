package com.veritaspay.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veritaspay.account.AccountCategory;
import com.veritaspay.account.LedgerAccount;
import com.veritaspay.account.LedgerAccountRepository;
import com.veritaspay.audit.AuditService;
import com.veritaspay.auth.AuthenticatedUser;
import com.veritaspay.auth.Role;
import com.veritaspay.common.BusinessException;
import com.veritaspay.common.MoneyUtils;
import com.veritaspay.ledger.*;
import com.veritaspay.outbox.OutboxService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentTransferService {

    private static final BigDecimal MERCHANT_FEE_RATE = new BigDecimal("0.0100"); // 1.00%

    private final LedgerAccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Counter completedCounter;
    private final Counter replayCounter;

    public PaymentTransferService(
            LedgerAccountRepository accountRepository,
            LedgerService ledgerService,
            IdempotencyRecordRepository idempotencyRepository,
            OutboxService outboxService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.completedCounter = Counter.builder("veritas.payments.completed")
                .description("Total completed payment transfers")
                .register(meterRegistry);
        this.replayCounter = Counter.builder("veritas.payments.idempotent.replay")
                .description("Total idempotent transfer replays served")
                .register(meterRegistry);
    }

    @Transactional
    public PaymentDtos.TransferResult executeTransfer(
            String idempotencyKey,
            PaymentDtos.TransferRequest request,
            AuthenticatedUser actor) {

        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header is required (1-128 characters)");
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT_TRANSFER",
                    "Source and destination accounts must be different");
        }

        BigDecimal grossAmount = MoneyUtils.normalize(request.amount());
        String requestHash = MoneyUtils.sha256(String.join("|",
                request.sourceAccountId().toString(),
                request.destinationAccountId().toString(),
                grossAmount.toPlainString(),
                request.currency(),
                Boolean.toString(request.merchantPayment())));

        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            return handleExistingIdempotency(existingOpt.get(), requestHash, actor);
        }

        IdempotencyRecord record = new IdempotencyRecord(
                idempotencyKey,
                actor.id(),
                requestHash,
                Instant.now().plus(24, ChronoUnit.HOURS)
        );
        try {
            idempotencyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONCURRENT_IDEMPOTENCY_KEY",
                    "A transfer with this Idempotency-Key is already being processed");
        }

        UUID sourceOwnerId = accountRepository.findOwnerIdById(request.sourceAccountId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "SOURCE_ACCOUNT_NOT_FOUND", "Source account not found"));

        if (actor.role() != Role.ADMIN && !actor.id().equals(sourceOwnerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN_SOURCE_ACCOUNT", "You do not own the source account");
        }

        List<DoubleEntryValidator.PostingLine> lines = new ArrayList<>();
        lines.add(new DoubleEntryValidator.PostingLine(
                request.sourceAccountId(), EntryType.DEBIT, grossAmount, request.currency()));

        BigDecimal fee = BigDecimal.ZERO.setScale(MoneyUtils.SCALE, RoundingMode.HALF_EVEN);
        if (request.merchantPayment()) {
            fee = MoneyUtils.normalize(grossAmount.multiply(MERCHANT_FEE_RATE));
            BigDecimal netToMerchant = grossAmount.subtract(fee);
            UUID feeAccountId = accountRepository.findIdsByAccountCategory(AccountCategory.FEE_REVENUE)
                    .stream().findFirst()
                    .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "FEE_ACCOUNT_MISSING", "System fee revenue account not configured"));

            lines.add(new DoubleEntryValidator.PostingLine(
                    request.destinationAccountId(), EntryType.CREDIT, netToMerchant, request.currency()));
            if (fee.signum() > 0) {
                lines.add(new DoubleEntryValidator.PostingLine(
                        feeAccountId, EntryType.CREDIT, fee, request.currency()));
            }
        } else {
            lines.add(new DoubleEntryValidator.PostingLine(
                    request.destinationAccountId(), EntryType.CREDIT, grossAmount, request.currency()));
        }

        TransactionType txType = request.merchantPayment()
                ? TransactionType.MERCHANT_PAYMENT
                : TransactionType.P2P_TRANSFER;

        LedgerDtos.JournalTransactionView txView = ledgerService.postJournalTransaction(
                txType,
                idempotencyKey,
                request.currency(),
                request.description() != null ? request.description() : txType.name(),
                actor.id(),
                lines
        );

        outboxService.enqueuePaymentEvent(
                txView.id().toString(),
                "PAYMENT_POSTED",
                new PaymentDtos.PaymentEventPayload(
                        txView.id(),
                        txView.referenceId(),
                        "PAYMENT_POSTED",
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        grossAmount,
                        request.currency(),
                        Instant.now()
                )
        );

        auditService.record(
                actor.id(),
                actor.email(),
                "PAYMENT_TRANSFER_EXECUTED",
                "JOURNAL_TRANSACTION",
                txView.id().toString(),
                String.format("ref=%s, amount=%s %s, fee=%s, idempotencyKey=%s",
                        txView.referenceId(), grossAmount, request.currency(), fee, idempotencyKey)
        );

        PaymentDtos.TransferResult result = new PaymentDtos.TransferResult(txView, false, idempotencyKey, fee);
        record.markCompleted(201, serializeResult(result), txView.id());
        idempotencyRepository.save(record);
        completedCounter.increment();

        return result;
    }

    @Transactional
    public PaymentDtos.TransferResult topUpWallet(PaymentDtos.TopUpRequest request, AuthenticatedUser actor) {
        UUID destinationOwnerId = accountRepository.findOwnerIdById(request.destinationAccountId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND", "Destination account not found"));

        if (actor.role() != Role.ADMIN && !actor.id().equals(destinationOwnerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN_ACCOUNT_ACCESS", "You can only top up your own account");
        }

        UUID treasuryId = accountRepository.findIdsByAccountCategory(AccountCategory.SYSTEM_TREASURY)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "TREASURY_MISSING", "System treasury account not configured"));

        BigDecimal amount = MoneyUtils.normalize(request.amount());
        List<DoubleEntryValidator.PostingLine> lines = List.of(
                new DoubleEntryValidator.PostingLine(treasuryId, EntryType.DEBIT, amount, request.currency()),
                new DoubleEntryValidator.PostingLine(request.destinationAccountId(), EntryType.CREDIT, amount, request.currency())
        );

        LedgerDtos.JournalTransactionView txView = ledgerService.postJournalTransaction(
                TransactionType.TOP_UP,
                null,
                request.currency(),
                request.description() != null ? request.description() : "Treasury Wallet Funding",
                actor.id(),
                lines
        );

        outboxService.enqueuePaymentEvent(
                txView.id().toString(),
                "WALLET_TOP_UP",
                new PaymentDtos.PaymentEventPayload(
                        txView.id(), txView.referenceId(), "WALLET_TOP_UP",
                        treasuryId, request.destinationAccountId(), amount, request.currency(), Instant.now()
                )
        );

        auditService.record(actor.id(), actor.email(), "WALLET_TOP_UP",
                "JOURNAL_TRANSACTION", txView.id().toString(),
                "ref=" + txView.referenceId() + ", amount=" + amount + " " + request.currency());

        completedCounter.increment();
        return new PaymentDtos.TransferResult(txView, false, null, BigDecimal.ZERO.setScale(MoneyUtils.SCALE));
    }

    private PaymentDtos.TransferResult handleExistingIdempotency(
            IdempotencyRecord existing,
            String requestHash,
            AuthenticatedUser actor) {
        if (!existing.getUserId().equals(actor.id()) && actor.role() != Role.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "IDEMPOTENCY_KEY_OWNERSHIP",
                    "Idempotency-Key belongs to a different principal");
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH",
                    "Idempotency-Key was previously used with different transfer parameters");
        }
        if (existing.getStatus() != IdempotencyStatus.COMPLETED || existing.getResponseBody() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                    "Transfer with this Idempotency-Key is currently in progress");
        }

        try {
            PaymentDtos.TransferResult cached = objectMapper.readValue(
                    existing.getResponseBody(), PaymentDtos.TransferResult.class);
            replayCounter.increment();
            return new PaymentDtos.TransferResult(
                    cached.transaction(),
                    true,
                    existing.getIdempotencyKey(),
                    cached.feeApplied()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached idempotent response", e);
        }
    }

    private String serializeResult(PaymentDtos.TransferResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize transfer result", e);
        }
    }
}
