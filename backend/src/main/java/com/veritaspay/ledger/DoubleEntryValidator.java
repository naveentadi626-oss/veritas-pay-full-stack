package com.veritaspay.ledger;

import com.veritaspay.common.BusinessException;
import com.veritaspay.common.MoneyUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Enforces fundamental double-entry bookkeeping invariants before any journal posting commits.
 */
@Component
public class DoubleEntryValidator {

    public record PostingLine(UUID accountId, EntryType entryType, BigDecimal amount, String currency) {}

    public void validateBalanced(List<PostingLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_JOURNAL_ENTRY",
                    "A double-entry journal transaction requires at least two posting lines");
        }

        String expectedCurrency = lines.getFirst().currency();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (PostingLine line : lines) {
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT",
                        "Each ledger entry amount must be strictly positive");
            }
            if (!expectedCurrency.equals(line.currency())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH",
                        "All entries in a single journal transaction must share the same currency");
            }
            BigDecimal normalized = MoneyUtils.normalize(line.amount());
            if (line.entryType() == EntryType.DEBIT) {
                totalDebits = totalDebits.add(normalized);
            } else {
                totalCredits = totalCredits.add(normalized);
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "UNBALANCED_LEDGER_POSTING",
                    String.format("Double-entry invariant violated: debits=%s, credits=%s",
                            totalDebits, totalCredits));
        }
    }
}
