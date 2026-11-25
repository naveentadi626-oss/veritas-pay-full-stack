package com.veritaspay.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JournalTransactionRepository extends JpaRepository<JournalTransaction, UUID> {

    Page<JournalTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT DISTINCT jt FROM JournalTransaction jt
            JOIN LedgerEntry le ON le.transactionId = jt.id
            JOIN LedgerAccount la ON la.id = le.accountId
            WHERE la.ownerId = :ownerId OR jt.initiatedBy = :ownerId
            ORDER BY jt.createdAt DESC
            """)
    Page<JournalTransaction> findVisibleToUser(@Param("ownerId") UUID ownerId, Pageable pageable);
}
