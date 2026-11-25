package com.veritaspay.ledger;

import com.veritaspay.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Double-Entry Ledger", description = "Immutable journal transactions, ledger lines, and trial balance verification")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/transactions")
    @Operation(summary = "List journal transactions with balanced DEBIT/CREDIT entries")
    public Page<LedgerDtos.JournalTransactionView> listTransactions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ledgerService.listTransactions(user, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/transactions/{txId}")
    @Operation(summary = "Inspect a single journal transaction and its debit/credit ledger entries")
    public LedgerDtos.JournalTransactionView getTransaction(
            @PathVariable UUID txId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ledgerService.getTransaction(txId, user);
    }

    @GetMapping("/reconciliation/trial-balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
    @Operation(summary = "Verify system-wide double-entry invariant (SUM(DEBIT) == SUM(CREDIT))")
    public LedgerDtos.TrialBalanceReport verifyTrialBalance() {
        return ledgerService.verifyTrialBalance();
    }
}
