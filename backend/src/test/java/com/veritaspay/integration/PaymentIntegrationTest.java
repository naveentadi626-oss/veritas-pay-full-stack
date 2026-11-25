package com.veritaspay.integration;

import com.veritaspay.account.AccountCategory;
import com.veritaspay.account.LedgerAccount;
import com.veritaspay.account.LedgerAccountRepository;
import com.veritaspay.account.NormalBalance;
import com.veritaspay.auth.AuthDtos;
import com.veritaspay.auth.Role;
import com.veritaspay.auth.User;
import com.veritaspay.auth.UserRepository;
import com.veritaspay.ledger.LedgerDtos;
import com.veritaspay.ledger.TransactionStatus;
import com.veritaspay.payment.PaymentDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LedgerAccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String customerToken;
    private UUID aliceWalletId;
    private UUID bobWalletId;
    private UUID merchantAccountId;

    @BeforeEach
    void setupAccounts() {
        if (accountRepository.findFirstByAccountCategory(AccountCategory.SYSTEM_TREASURY).isEmpty()) {
            User admin = userRepository.save(new User(
                    UUID.randomUUID(), "admin@test.io", "Admin",
                    passwordEncoder.encode("SecretPass123!"), Role.ADMIN));
            accountRepository.save(new LedgerAccount(
                    UUID.randomUUID(), "VP-TREASURY-TEST", admin.getId(), "Test Treasury",
                    AccountCategory.SYSTEM_TREASURY, NormalBalance.DEBIT, "USD", true));
            accountRepository.save(new LedgerAccount(
                    UUID.randomUUID(), "VP-FEE-TEST", admin.getId(), "Test Fee",
                    AccountCategory.FEE_REVENUE, NormalBalance.CREDIT, "USD", false));
        }

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        AuthDtos.AuthResponse aliceAuth = registerUser("alice-" + uniqueSuffix + "@test.io", "Alice", Role.CUSTOMER);
        AuthDtos.AuthResponse bobAuth = registerUser("bob-" + uniqueSuffix + "@test.io", "Bob", Role.CUSTOMER);
        AuthDtos.AuthResponse merchantAuth = registerUser("merch-" + uniqueSuffix + "@test.io", "Merchant", Role.MERCHANT);
        AuthDtos.AuthResponse adminAuth = login("admin@test.io", "SecretPass123!");

        customerToken = aliceAuth.accessToken();
        adminToken = adminAuth.accessToken();

        aliceWalletId = createAccount(customerToken, "Alice Wallet", AccountCategory.USER_WALLET);
        bobWalletId = createAccount(bobAuth.accessToken(), "Bob Wallet", AccountCategory.USER_WALLET);
        merchantAccountId = createAccount(merchantAuth.accessToken(), "Merchant Settlement", AccountCategory.MERCHANT_SETTLEMENT);

        topUp(customerToken, aliceWalletId, new BigDecimal("2000.00"));
        topUp(bobAuth.accessToken(), bobWalletId, new BigDecimal("2000.00"));
    }

    @Test
    void idempotentMerchantTransferPreventsDoubleChargeAndBalancesLedger() throws InterruptedException {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        PaymentDtos.TransferRequest req = new PaymentDtos.TransferRequest(
                aliceWalletId, merchantAccountId, new BigDecimal("500.00"), "USD",
                "Enterprise SaaS Subscription", true);

        HttpHeaders headers = authHeaders(customerToken);
        headers.set("Idempotency-Key", idempotencyKey);

        ResponseEntity<PaymentDtos.TransferResult> first = restTemplate.exchange(
                "/api/v1/payments/transfers", HttpMethod.POST, new HttpEntity<>(req, headers), PaymentDtos.TransferResult.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().idempotentReplayed()).isFalse();
        assertThat(first.getBody().feeApplied()).isEqualByComparingTo("5.0000");

        // Replay with same key & same payload -> 200 OK, no second debit!
        ResponseEntity<PaymentDtos.TransferResult> replay = restTemplate.exchange(
                "/api/v1/payments/transfers", HttpMethod.POST, new HttpEntity<>(req, headers), PaymentDtos.TransferResult.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("X-Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.getBody()).isNotNull();
        assertThat(replay.getBody().idempotentReplayed()).isTrue();
        assertThat(replay.getBody().transaction().id()).isEqualTo(first.getBody().transaction().id());

        // Same key with tampered amount -> 409 Conflict
        PaymentDtos.TransferRequest tampered = new PaymentDtos.TransferRequest(
                aliceWalletId, merchantAccountId, new BigDecimal("999.00"), "USD", "Tampered", true);
        ResponseEntity<String> conflict = restTemplate.exchange(
                "/api/v1/payments/transfers", HttpMethod.POST, new HttpEntity<>(tampered, headers), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Wait briefly for OutboxRelayScheduler -> Kafka -> PaymentEventConsumer settlement
        UUID txId = first.getBody().transaction().id();
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        TransactionStatus latestStatus = TransactionStatus.POSTED;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<LedgerDtos.JournalTransactionView> txRes = restTemplate.exchange(
                    "/api/v1/ledger/transactions/" + txId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(customerToken)), LedgerDtos.JournalTransactionView.class);
            if (txRes.getBody() != null && txRes.getBody().status() == TransactionStatus.SETTLED) {
                latestStatus = TransactionStatus.SETTLED;
                break;
            }
            Thread.sleep(300);
        }
        assertThat(latestStatus).isEqualTo(TransactionStatus.SETTLED);

        // Verify system-wide trial balance invariant: SUM(DEBIT) == SUM(CREDIT)
        ResponseEntity<LedgerDtos.TrialBalanceReport> trial = restTemplate.exchange(
                "/api/v1/ledger/reconciliation/trial-balance", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), LedgerDtos.TrialBalanceReport.class);
        assertThat(trial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(trial.getBody()).isNotNull();
        assertThat(trial.getBody().balanced()).isTrue();
        assertThat(trial.getBody().discrepancy()).isEqualByComparingTo("0.0000");
    }

    @Test
    void opposingConcurrentTransfersExecuteWithoutDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<ResponseEntity<PaymentDtos.TransferResult>>> tasks = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            tasks.add(() -> {
                boolean forward = idx % 2 == 0;
                UUID from = forward ? aliceWalletId : bobWalletId;
                UUID to = forward ? bobWalletId : aliceWalletId;
                HttpHeaders headers = authHeaders(adminToken);
                headers.set("Idempotency-Key", "conc-" + UUID.randomUUID());
                PaymentDtos.TransferRequest req = new PaymentDtos.TransferRequest(
                        from, to, new BigDecimal("25.00"), "USD", "Concurrent test " + idx, false);
                return restTemplate.exchange(
                        "/api/v1/payments/transfers", HttpMethod.POST, new HttpEntity<>(req, headers), PaymentDtos.TransferResult.class);
            });
        }

        List<Future<ResponseEntity<PaymentDtos.TransferResult>>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        for (Future<ResponseEntity<PaymentDtos.TransferResult>> f : futures) {
            assertThat(f.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        LedgerAccount aliceAfter = accountRepository.findById(aliceWalletId).orElseThrow();
        LedgerAccount bobAfter = accountRepository.findById(bobWalletId).orElseThrow();
        assertThat(aliceAfter.getBalance().add(bobAfter.getBalance())).isEqualByComparingTo("4000.0000");
    }

    private AuthDtos.AuthResponse registerUser(String email, String name, Role role) {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest(email, name, "VeritasDemo@2026", role);
        return restTemplate.postForEntity("/api/v1/auth/register", req, AuthDtos.AuthResponse.class).getBody();
    }

    private AuthDtos.AuthResponse login(String email, String password) {
        return restTemplate.postForEntity(
                "/api/v1/auth/login", new AuthDtos.LoginRequest(email, password), AuthDtos.AuthResponse.class).getBody();
    }

    private UUID createAccount(String token, String name, AccountCategory category) {
        HttpHeaders headers = authHeaders(token);
        var req = new com.veritaspay.account.AccountDtos.CreateAccountRequest(name, category, "USD");
        var res = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST, new HttpEntity<>(req, headers),
                com.veritaspay.account.AccountDtos.AccountSummary.class);
        return res.getBody().id();
    }

    private void topUp(String token, UUID accountId, BigDecimal amount) {
        HttpHeaders headers = authHeaders(token);
        var req = new PaymentDtos.TopUpRequest(accountId, amount, "USD", "Test funding");
        restTemplate.exchange("/api/v1/payments/top-ups", HttpMethod.POST, new HttpEntity<>(req, headers), String.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
