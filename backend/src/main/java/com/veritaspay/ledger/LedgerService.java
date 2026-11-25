package com.veritaspay.ledger;

import com.veritaspay.account.LedgerAccount;
import com.veritaspay.account.LedgerAccountRepository;
import com.veritaspay.auth.AuthenticatedUser;
import com.veritaspay.auth.Role;
import com.veritaspay.common.BusinessException;
import com.veritaspay.common.CorrelationIdFilter;
import com.veritaspay.common.MoneyUtils;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final LedgerAccountRepository accountRepository;
    private final JournalTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final DoubleEntryValidator validator;

    public LedgerService(
            LedgerAccountRepository accountRepository,
            JournalTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository,
            DoubleEntryValidator validator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.validator = validator;
    }

    /**
     * Posts a balanced double-entry journal transaction with deterministic row-level lock ordering
     * to prevent concurrent deadlocks and lost updates.
     */
    @Transactional
    public LedgerDtos.JournalTransactionView postJournalTransaction(
            TransactionType transactionType,
            String idempotencyKey,
            String currency,
            String description,
            UUID initiatedBy,
            List<DoubleEntryValidator.PostingLine> lines) {

        validator.validateBalanced(lines);

        // Deterministic ascending UUID lock ordering prevents deadlocks under concurrent A->B & B->A transfers
        List<UUID> sortedAccountIds = lines.stream()
                .map(DoubleEntryValidator.PostingLine::accountId)
                .distinct()
                .sorted()
                .toList();

        List<LedgerAccount> lockedAccounts = accountRepository.findAllByIdInForUpdateOrdered(sortedAccountIds);
        if (lockedAccounts.size() != sortedAccountIds.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                    "One or more ledger accounts do not exist");
        }

        Map<UUID, LedgerAccount> accountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));

        BigDecimal totalDebits = lines.stream()
                .filter(l -> l.entryType() == EntryType.DEBIT)
                .map(l -> MoneyUtils.normalize(l.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        UUID txId = UUID.randomUUID();
        String referenceId = "TXN-" + txId.toString().substring(0, 8).toUpperCase();

        JournalTransaction tx = new JournalTransaction(
                txId,
                referenceId,
                idempotencyKey,
                transactionType,
                TransactionStatus.POSTED,
                currency,
                MoneyUtils.normalize(totalDebits),
                description,
                initiatedBy,
                MDC.get(CorrelationIdFilter.MDC_KEY)
        );
        transactionRepository.save(tx);

        List<LedgerEntry> savedEntries = new ArrayList<>(lines.size());
        for (DoubleEntryValidator.PostingLine line : lines) {
            LedgerAccount account = accountMap.get(line.accountId());
            if (!account.getCurrency().equals(line.currency())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ACCOUNT_CURRENCY_MISMATCH",
                        "Account " + account.getAccountNumber() + " currency is " + account.getCurrency());
            }
            BigDecimal balanceAfter = account.applyEntry(line.entryType(), line.amount());
            LedgerEntry entry = new LedgerEntry(
                    UUID.randomUUID(),
                    tx.getId(),
                    account.getId(),
                    line.entryType(),
                    MoneyUtils.normalize(line.amount()),
                    line.currency(),
                    balanceAfter
            );
            savedEntries.add(entryRepository.save(entry));
        }

        accountRepository.saveAll(lockedAccounts);
        return toView(tx, savedEntries, accountMap);
    }

    @Transactional(readOnly = true)
    public Page<LedgerDtos.JournalTransactionView> listTransactions(AuthenticatedUser user, Pageable pageable) {
        Page<JournalTransaction> page = (user.role() == Role.ADMIN || user.role() == Role.COMPLIANCE_OFFICER)
                ? transactionRepository.findAllByOrderByCreatedAtDesc(pageable)
                : transactionRepository.findVisibleToUser(user.id(), pageable);

        return page.map(tx -> {
            List<LedgerEntry> entries = entryRepository.findByTransactionIdOrderByEntryTypeAscCreatedAtAsc(tx.getId());
            Map<UUID, LedgerAccount> accountMap = resolveAccounts(entries);
            return toView(tx, entries, accountMap);
        });
    }

    @Transactional(readOnly = true)
    public LedgerDtos.JournalTransactionView getTransaction(UUID txId, AuthenticatedUser user) {
        JournalTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "TRANSACTION_NOT_FOUND", "Journal transaction not found"));

        List<LedgerEntry> entries = entryRepository.findByTransactionIdOrderByEntryTypeAscCreatedAtAsc(txId);
        Map<UUID, LedgerAccount> accountMap = resolveAccounts(entries);

        boolean privileged = user.role() == Role.ADMIN || user.role() == Role.COMPLIANCE_OFFICER;
        boolean participant = user.id().equals(tx.getInitiatedBy()) || accountMap.values().stream()
                .anyMatch(acc -> user.id().equals(acc.getOwnerId()));
        if (!privileged && !participant) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN_TRANSACTION_ACCESS", "You are not authorized to view this transaction");
        }
        return toView(tx, entries, accountMap);
    }

    @Transactional(readOnly = true)
    public LedgerDtos.TrialBalanceReport verifyTrialBalance() {
        BigDecimal totalDebits = MoneyUtils.normalize(entryRepository.sumByEntryType(EntryType.DEBIT));
        BigDecimal totalCredits = MoneyUtils.normalize(entryRepository.sumByEntryType(EntryType.CREDIT));
        BigDecimal discrepancy = totalDebits.subtract(totalCredits).abs();
        return new LedgerDtos.TrialBalanceReport(
                discrepancy.compareTo(BigDecimal.ZERO) == 0,
                totalDebits,
                totalCredits,
                discrepancy,
                entryRepository.count(),
                transactionRepository.count(),
                Instant.now()
        );
    }

    private Map<UUID, LedgerAccount> resolveAccounts(List<LedgerEntry> entries) {
        Set<UUID> ids = entries.stream().map(LedgerEntry::getAccountId).collect(Collectors.toSet());
        return accountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));
    }

    private LedgerDtos.JournalTransactionView toView(
            JournalTransaction tx,
            List<LedgerEntry> entries,
            Map<UUID, LedgerAccount> accountMap) {
        List<LedgerDtos.LedgerEntryView> entryViews = entries.stream()
                .map(e -> {
                    LedgerAccount acc = accountMap.get(e.getAccountId());
                    return new LedgerDtos.LedgerEntryView(
                            e.getId(),
                            e.getAccountId(),
                            acc != null ? acc.getAccountNumber() : "UNKNOWN",
                            acc != null ? acc.getName() : "Unknown Account",
                            e.getEntryType(),
                            e.getAmount(),
                            e.getCurrency(),
                            e.getBalanceAfter(),
                            e.getCreatedAt()
                    );
                })
                .toList();

        return new LedgerDtos.JournalTransactionView(
                tx.getId(),
                tx.getReferenceId(),
                tx.getIdempotencyKey(),
                tx.getTransactionType(),
                tx.getStatus(),
                tx.getCurrency(),
                tx.getTotalAmount(),
                tx.getDescription(),
                tx.getCorrelationId(),
                tx.getCreatedAt(),
                entryViews
        );
    }
}
