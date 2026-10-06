package com.finledger.ledger;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    @EntityGraph(attributePaths = "entries")
    Optional<LedgerTransaction> findByTransferId(Long transferId);
}
