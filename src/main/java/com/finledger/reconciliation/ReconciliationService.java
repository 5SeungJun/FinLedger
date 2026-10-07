package com.finledger.reconciliation;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository){
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public ReconciliationResult reconcile(Long accountId, long baseBalance) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌가 없습니다."));

        long ledgerDelta = ledgerEntryRepository.sumAmountByAccountId(accountId);
        long expectedBalance = baseBalance + ledgerDelta;
        long actualBalance = account.getBalance();
        long difference = actualBalance - expectedBalance;

        return new ReconciliationResult(
                accountId,
                baseBalance,
                ledgerDelta,
                expectedBalance,
                actualBalance,
                difference,
                difference == 0
        );
    }
}
