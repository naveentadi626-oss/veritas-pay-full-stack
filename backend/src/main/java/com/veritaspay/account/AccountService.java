package com.veritaspay.account;

import com.veritaspay.audit.AuditService;
import com.veritaspay.auth.AuthenticatedUser;
import com.veritaspay.auth.Role;
import com.veritaspay.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    private final LedgerAccountRepository accountRepository;
    private final AuditService auditService;

    public AccountService(LedgerAccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountSummary> listVisibleAccounts(AuthenticatedUser user) {
        List<LedgerAccount> accounts;
        if (user.role() == Role.ADMIN || user.role() == Role.COMPLIANCE_OFFICER) {
            accounts = accountRepository.findAll();
        } else {
            accounts = accountRepository.findByOwnerIdOrderByCreatedAtAsc(user.id());
        }
        return accounts.stream().map(AccountDtos.AccountSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountSummary> listTransferDirectory() {
        return accountRepository.findAll().stream()
                .filter(a -> a.getAccountCategory() == AccountCategory.USER_WALLET
                        || a.getAccountCategory() == AccountCategory.MERCHANT_SETTLEMENT)
                .map(AccountDtos.AccountSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountDtos.AccountSummary getAccount(UUID accountId, AuthenticatedUser user) {
        LedgerAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND", "Ledger account not found"));

        boolean privileged = user.role() == Role.ADMIN || user.role() == Role.COMPLIANCE_OFFICER;
        if (!privileged && !user.id().equals(account.getOwnerId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN_ACCOUNT_ACCESS", "You do not own this account");
        }
        return AccountDtos.AccountSummary.from(account);
    }

    @Transactional
    public AccountDtos.AccountSummary createAccount(AccountDtos.CreateAccountRequest request, AuthenticatedUser user) {
        AccountCategory category = request.category() != null ? request.category() : AccountCategory.USER_WALLET;
        if ((category == AccountCategory.SYSTEM_TREASURY || category == AccountCategory.FEE_REVENUE)
                && user.role() != Role.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN_ACCOUNT_CATEGORY", "Only ADMIN can create system accounts");
        }

        String currency = request.currency() != null ? request.currency() : "USD";
        String accountNumber = "VP-" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
        NormalBalance normalBalance = category == AccountCategory.SYSTEM_TREASURY
                ? NormalBalance.DEBIT
                : NormalBalance.CREDIT;

        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                accountNumber,
                user.id(),
                request.name().trim(),
                category,
                normalBalance,
                currency,
                false
        );
        accountRepository.save(account);
        auditService.record(user.id(), user.email(), "ACCOUNT_CREATED",
                "LEDGER_ACCOUNT", account.getId().toString(),
                "accountNumber=" + accountNumber + ", category=" + category);

        return AccountDtos.AccountSummary.from(account);
    }
}
