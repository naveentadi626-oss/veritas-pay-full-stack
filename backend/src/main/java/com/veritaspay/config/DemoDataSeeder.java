package com.veritaspay.config;

import com.veritaspay.account.AccountCategory;
import com.veritaspay.account.LedgerAccount;
import com.veritaspay.account.LedgerAccountRepository;
import com.veritaspay.account.NormalBalance;
import com.veritaspay.auth.Role;
import com.veritaspay.auth.User;
import com.veritaspay.auth.UserRepository;
import com.veritaspay.ledger.DoubleEntryValidator;
import com.veritaspay.ledger.EntryType;
import com.veritaspay.ledger.LedgerService;
import com.veritaspay.ledger.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Seeds realistic FinTech personas and balanced opening double-entry ledger balances on fresh environments.
 */
@Component
@Profile("!test")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            UserRepository userRepository,
            LedgerAccountRepository accountRepository,
            LedgerService ledgerService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        String demoHash = passwordEncoder.encode("VeritasDemo@2026");

        User alice = userRepository.save(new User(
                UUID.randomUUID(), "alice@veritaspay.io", "Alice Chen", demoHash, Role.CUSTOMER));
        User bob = userRepository.save(new User(
                UUID.randomUUID(), "bob@veritaspay.io", "Bob Rivera", demoHash, Role.CUSTOMER));
        User merchant = userRepository.save(new User(
                UUID.randomUUID(), "merchant@apexcloud.io", "Apex Cloud Services", demoHash, Role.MERCHANT));
        userRepository.save(new User(
                UUID.randomUUID(), "compliance@veritaspay.io", "Priya Nair (Compliance)", demoHash, Role.COMPLIANCE_OFFICER));
        User admin = userRepository.save(new User(
                UUID.randomUUID(), "admin@veritaspay.io", "Marcus Vance (VP Eng)", demoHash, Role.ADMIN));

        LedgerAccount treasury = accountRepository.save(new LedgerAccount(
                UUID.randomUUID(), "VP-TREASURY-01", admin.getId(), "Veritas Federal Reserve Treasury",
                AccountCategory.SYSTEM_TREASURY, NormalBalance.DEBIT, "USD", true));
        accountRepository.save(new LedgerAccount(
                UUID.randomUUID(), "VP-FEES-01", admin.getId(), "Platform Fee Revenue",
                AccountCategory.FEE_REVENUE, NormalBalance.CREDIT, "USD", false));

        LedgerAccount aliceWallet = accountRepository.save(new LedgerAccount(
                UUID.randomUUID(), "VP-10010001", alice.getId(), "Alice Operating Wallet",
                AccountCategory.USER_WALLET, NormalBalance.CREDIT, "USD", false));
        LedgerAccount bobWallet = accountRepository.save(new LedgerAccount(
                UUID.randomUUID(), "VP-10010002", bob.getId(), "Bob Personal Wallet",
                AccountCategory.USER_WALLET, NormalBalance.CREDIT, "USD", false));
        LedgerAccount apexSettlement = accountRepository.save(new LedgerAccount(
                UUID.randomUUID(), "VP-20010001", merchant.getId(), "Apex Cloud Settlement",
                AccountCategory.MERCHANT_SETTLEMENT, NormalBalance.CREDIT, "USD", false));

        fundAccount(treasury, aliceWallet, new BigDecimal("25000.00"), "Opening Treasury Settlement - Alice", admin.getId());
        fundAccount(treasury, bobWallet, new BigDecimal("8500.00"), "Opening Treasury Settlement - Bob", admin.getId());
        fundAccount(treasury, apexSettlement, new BigDecimal("12500.00"), "Opening Treasury Settlement - Apex Cloud", admin.getId());

        // Seed one merchant payment with 1% fee so ledger explorer shows a 3-line balanced entry out of the box
        ledgerService.postJournalTransaction(
                TransactionType.MERCHANT_PAYMENT,
                "seed-tx-001",
                "USD",
                "Cloud Infrastructure Invoice #INV-2026-01",
                alice.getId(),
                List.of(
                        new DoubleEntryValidator.PostingLine(aliceWallet.getId(), EntryType.DEBIT, new BigDecimal("1000.00"), "USD"),
                        new DoubleEntryValidator.PostingLine(apexSettlement.getId(), EntryType.CREDIT, new BigDecimal("990.00"), "USD"),
                        new DoubleEntryValidator.PostingLine(
                                accountRepository.findFirstByAccountCategory(AccountCategory.FEE_REVENUE).orElseThrow().getId(),
                                EntryType.CREDIT, new BigDecimal("10.00"), "USD")
                )
        );

        log.info("Seeded VeritasPay demo users, ledger accounts, and balanced opening transactions.");
    }

    private void fundAccount(LedgerAccount treasury, LedgerAccount target, BigDecimal amount, String desc, UUID actorId) {
        ledgerService.postJournalTransaction(
                TransactionType.TOP_UP,
                null,
                "USD",
                desc,
                actorId,
                List.of(
                        new DoubleEntryValidator.PostingLine(treasury.getId(), EntryType.DEBIT, amount, "USD"),
                        new DoubleEntryValidator.PostingLine(target.getId(), EntryType.CREDIT, amount, "USD")
                )
        );
    }
}
