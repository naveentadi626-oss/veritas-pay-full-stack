package com.veritaspay.unit;

import com.veritaspay.common.BusinessException;
import com.veritaspay.ledger.DoubleEntryValidator;
import com.veritaspay.ledger.EntryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoubleEntryValidatorTest {

    private final DoubleEntryValidator validator = new DoubleEntryValidator();

    @Test
    void acceptsBalancedThreeLinePosting() {
        List<DoubleEntryValidator.PostingLine> lines = List.of(
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.DEBIT, new BigDecimal("100.00"), "USD"),
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.CREDIT, new BigDecimal("99.00"), "USD"),
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.CREDIT, new BigDecimal("1.00"), "USD")
        );

        assertThatCode(() -> validator.validateBalanced(lines)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedPosting() {
        List<DoubleEntryValidator.PostingLine> lines = List.of(
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.DEBIT, new BigDecimal("100.00"), "USD"),
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.CREDIT, new BigDecimal("99.50"), "USD")
        );

        assertThatThrownBy(() -> validator.validateBalanced(lines))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Double-entry invariant violated");
    }

    @Test
    void rejectsCurrencyMismatch() {
        List<DoubleEntryValidator.PostingLine> lines = List.of(
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.DEBIT, new BigDecimal("50.00"), "USD"),
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.CREDIT, new BigDecimal("50.00"), "EUR")
        );

        assertThatThrownBy(() -> validator.validateBalanced(lines))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void rejectsSingleLinePosting() {
        List<DoubleEntryValidator.PostingLine> lines = List.of(
                new DoubleEntryValidator.PostingLine(UUID.randomUUID(), EntryType.DEBIT, new BigDecimal("10.00"), "USD")
        );

        assertThatThrownBy(() -> validator.validateBalanced(lines))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least two");
    }
}
