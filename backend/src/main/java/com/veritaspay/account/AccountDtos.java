package com.veritaspay.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {

    public record CreateAccountRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            AccountCategory category,
            @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code") String currency
    ) {}

    public record AccountSummary(
            UUID id,
            String accountNumber,
            UUID ownerId,
            String name,
            AccountCategory category,
            NormalBalance normalBalance,
            String currency,
            BigDecimal balance,
            String status,
            Instant updatedAt
    ) {
        public static AccountSummary from(LedgerAccount account) {
            return new AccountSummary(
                    account.getId(),
                    account.getAccountNumber(),
                    account.getOwnerId(),
                    account.getName(),
                    account.getAccountCategory(),
                    account.getNormalBalance(),
                    account.getCurrency(),
                    account.getBalance(),
                    account.getStatus(),
                    account.getUpdatedAt()
            );
        }
    }
}
