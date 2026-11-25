package com.veritaspay.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    List<LedgerAccount> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<LedgerAccount> findByAccountNumber(String accountNumber);

    Optional<LedgerAccount> findFirstByAccountCategory(AccountCategory category);

    @Query("SELECT a.ownerId FROM LedgerAccount a WHERE a.id = :id")
    Optional<UUID> findOwnerIdById(@Param("id") UUID id);

    @Query("SELECT a.id FROM LedgerAccount a WHERE a.accountCategory = :category")
    List<UUID> findIdsByAccountCategory(@Param("category") AccountCategory category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LedgerAccount a WHERE a.id IN :ids ORDER BY a.id ASC")
    List<LedgerAccount> findAllByIdInForUpdateOrdered(@Param("ids") List<UUID> ids);
}
