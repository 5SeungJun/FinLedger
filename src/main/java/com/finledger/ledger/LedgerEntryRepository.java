package com.finledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    @Query("select coalesce(sum(le.amount), 0) from LedgerEntry le where le.account.id =:accountId")
    long sumAmountByAccountId(@Param("accountId") Long accountId);
}
