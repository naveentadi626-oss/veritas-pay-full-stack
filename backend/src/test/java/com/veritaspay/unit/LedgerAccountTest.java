package com.veritaspay.unit;

import com.veritaspay.account.AccountCategory;
import com.veritaspay.account.LedgerAccount;
import com.veritaspay.account.NormalBalance;
import com.veritaspay.common.BusinessException;
import com.veritaspay.ledger.EntryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerAccountTest {

    @Test
    void creditNormalWalletIncreasesOnCreditAndDecreasesOnDebit() {
        LedgerAccount wallet = new LedgerAccount(
                UUID.randomUUID(), "VP-1001", UUID.randomUUID(), "Test Wallet",
                AccountCategory.USER_WALLET, NormalBalance.CREDIT, "USD", false);

        wallet.applyEntry(EntryType.CREDIT, new BigDecimal("500.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("500.0000");

        wallet.applyEntry(EntryType.DEBIT, new BigDecimal("125.50"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("374.5000");
    }

    @Test
    void preventsOverdraftWhenNegativeDisallowed() {
        LedgerAccount wallet = new LedgerAccount(
                UUID.randomUUID(), "VP-1002", UUID.randomUUID(), "No Overdraft Wallet",
                AccountCategory.USER_WALLET, NormalBalance.CREDIT, "USD", false);
        wallet.applyEntry(EntryType.CREDIT, new BigDecimal("50.00"));

        assertThatThrownBy(() -> wallet.applyEntry(EntryType.DEBIT, new BigDecimal("50.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void debitNormalTreasuryIncreasesOnDebit() {
        LedgerAccount treasury = new LedgerAccount(
                UUID.randomUUID(), "VP-TREASURY", UUID.randomUUID(), "Treasury",
                AccountCategory.SYSTEM_TREASURY, NormalBalance.DEBIT, "USD", true);

        treasury.applyEntry(EntryType.DEBIT, new BigDecimal("1000.00"));
        assertThat(treasury.getBalance()).isEqualByComparingTo("1000.0000");
    }
}
