package com.veritaspay.account;

import com.veritaspay.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Ledger Accounts", description = "Wallet, merchant settlement, and treasury accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List accounts owned by current user (or all accounts for ADMIN/COMPLIANCE)")
    public List<AccountDtos.AccountSummary> listAccounts(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.listVisibleAccounts(user);
    }

    @GetMapping("/directory")
    @Operation(summary = "List active recipient wallets and merchant settlement accounts for transfers")
    public List<AccountDtos.AccountSummary> listTransferDirectory() {
        return accountService.listTransferDirectory();
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details")
    public AccountDtos.AccountSummary getAccount(
            @PathVariable UUID accountId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.getAccount(accountId, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new wallet or merchant settlement account")
    public AccountDtos.AccountSummary createAccount(
            @Valid @RequestBody AccountDtos.CreateAccountRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.createAccount(request, user);
    }
}
