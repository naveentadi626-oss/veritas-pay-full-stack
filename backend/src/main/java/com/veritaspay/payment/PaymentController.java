package com.veritaspay.payment;

import com.veritaspay.auth.AuthenticatedUser;
import com.veritaspay.outbox.OutboxEvent;
import com.veritaspay.outbox.OutboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments & Outbox", description = "Idempotent double-entry payment transfers, top-ups, and Kafka outbox stream")
public class PaymentController {

    private final PaymentTransferService transferService;
    private final OutboxService outboxService;

    public PaymentController(PaymentTransferService transferService, OutboxService outboxService) {
        this.transferService = transferService;
        this.outboxService = outboxService;
    }

    @PostMapping("/transfers")
    @Operation(summary = "Execute an idempotent double-entry payment transfer")
    public ResponseEntity<PaymentDtos.TransferResult> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentDtos.TransferRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        PaymentDtos.TransferResult result = transferService.executeTransfer(idempotencyKey, request, user);
        HttpStatus status = result.idempotentReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header("X-Idempotent-Replayed", String.valueOf(result.idempotentReplayed()))
                .body(result);
    }

    @PostMapping("/top-ups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Fund a wallet from System Treasury (double-entry balanced)")
    public PaymentDtos.TransferResult topUp(
            @Valid @RequestBody PaymentDtos.TopUpRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return transferService.topUpWallet(request, user);
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
    @Operation(summary = "Inspect Transactional Outbox events and Kafka publication status")
    public Page<OutboxEvent> listOutboxEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return outboxService.listRecentEvents(PageRequest.of(page, Math.min(size, 100)));
    }
}
