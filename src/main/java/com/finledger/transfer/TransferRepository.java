package com.finledger.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByFromAccount_IdOrToAccount_IdOrderByCreatedAtDesc(Long fromAccountId, Long toAccountId);
}
