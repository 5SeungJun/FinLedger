package com.finledger.ledger;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.transfer.Transfer;
import com.finledger.transfer.TransferRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReversalService {
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    public ReversalService(TransferRepository transferRepository, AccountRepository accountRepository, LedgerTransactionRepository ledgerTransactionRepository){
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
    }

    @Transactional
    public void reverse(Long transferId){
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(()-> new IllegalArgumentException("송금이 없습니다."));

        LedgerTransaction original = ledgerTransactionRepository.findByTransferId(transferId)
                .orElseThrow(()-> new IllegalStateException("원장이 없습니다."));

        if(ledgerTransactionRepository.existsByReversalOfId(original.getId())){
            throw new IllegalStateException("이미 취소된 거래입니다.");
        }

        Long fromId = transfer.getFromAccount().getId();
        Long toId = transfer.getToAccount().getId();

        //DeadLock 방지를 위해 기존 Lock Ordering 유지
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow();

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow();

        Account from = fromId.equals(first.getId())? first : second;
        Account to = toId.equals(first.getId())? first : second;

        to.withdraw(transfer.getAmount());
        from.deposit(transfer.getAmount());

        LedgerTransaction reversal = LedgerTransaction.reversalOf(original, LocalDateTime.now());

        //원본 Entry의 부호를 뒤집음
        for(LedgerEntry entry : original.getEntries()){
            reversal.addEntry(entry.getAccount(), -entry.getAmount());
        }

        reversal.validateBalanced();
        ledgerTransactionRepository.save(reversal);

        transfer.reverse();
    }
}
