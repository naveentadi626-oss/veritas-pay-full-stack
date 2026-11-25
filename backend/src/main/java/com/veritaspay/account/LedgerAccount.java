package com.veritaspay.account;

import com.veritaspay.common.BusinessException;
import com.veritaspay.common.MoneyUtils;
import com.veritaspay.ledger.EntryType;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    private String accountNumber;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", nullable = false, length = 40)
    private AccountCategory accountCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private NormalBalance normalBalance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(nullable = false, length = 20)
    private String status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAccount() {}

    public LedgerAccount(UUID id, String accountNumber, UUID ownerId, String name,
                         AccountCategory accountCategory, NormalBalance normalBalance,
                         String currency, boolean allowNegative) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.ownerId = ownerId;
        this.name = name;
        this.accountCategory = accountCategory;
        this.normalBalance = normalBalance;
        this.currency = currency;
        this.balance = MoneyUtils.normalize(BigDecimal.ZERO);
        this.allowNegative = allowNegative;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a double-entry posting to this account respecting its normal balance side.
     * DEBIT-normal (Asset/Expense): DEBIT increases, CREDIT decreases.
     * CREDIT-normal (Liability/Wallet/Revenue): CREDIT increases, DEBIT decreases.
     */
    public BigDecimal applyEntry(EntryType entryType, BigDecimal amount) {
        BigDecimal normalized = MoneyUtils.normalize(amount);
        if (normalized.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT",
                    "Ledger entry amount must be strictly positive");
        }
        if (!"ACTIVE".equals(status)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_INACTIVE",
                    "Account " + accountNumber + " is not active");
        }

        boolean increasesBalance = (normalBalance == NormalBalance.DEBIT && entryType == EntryType.DEBIT)
                || (normalBalance == NormalBalance.CREDIT && entryType == EntryType.CREDIT);

        BigDecimal nextBalance = increasesBalance
                ? this.balance.add(normalized)
                : this.balance.subtract(normalized);

        if (!allowNegative && nextBalance.signum() < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
                    "Insufficient balance in account " + accountNumber);
        }

        this.balance = MoneyUtils.normalize(nextBalance);
        this.updatedAt = Instant.now();
        return this.balance;
    }

    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public AccountCategory getAccountCategory() { return accountCategory; }
    public NormalBalance getNormalBalance() { return normalBalance; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public boolean isAllowNegative() { return allowNegative; }
    public String getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
