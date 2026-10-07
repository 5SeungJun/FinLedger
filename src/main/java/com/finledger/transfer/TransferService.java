package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.ledger.LedgerTransaction;
import com.finledger.ledger.LedgerTransactionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository, LedgerTransactionRepository ledgerTransactionRepository){
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
    }

    @Transactional
    public Transfer transfer(Long fromAccountId, Long toAccountId, long amount){
        if(amount <= 0){
            throw new IllegalArgumentException("송금 금액은 1원 이상이어야 합니다.");
        }
        if(fromAccountId.equals(toAccountId)){
            throw new IllegalArgumentException(
                    "같은 계좌로 송금할 수 없습니다."
            );
        }
        //DeadLock 방지를 위한 일관된 Lock 순서
        Long firstLockId = Math.min(fromAccountId, toAccountId);
        Long secondLockId = Math.max(fromAccountId, toAccountId);

        Account first = accountRepository.findByIdForUpdate(firstLockId).orElseThrow();
        Account second = accountRepository.findByIdForUpdate(secondLockId).orElseThrow();

        Account from = fromAccountId.equals(first.getId())? first : second;
        Account to = toAccountId.equals(first.getId())? first : second;

        //송금 생성
        Transfer transfer = new Transfer(from, to, amount);
        transferRepository.save(transfer);

        //실제 잔액 변경
        from.withdraw(amount);
        to.deposit(amount);

        //원장 생성
        LedgerTransaction ledger = LedgerTransaction.forTransfer(transfer, LocalDateTime.now());

        ledger.addEntry(from, -amount);
        ledger.addEntry(to, amount);

        //복식 원장 불변식 검증
        ledger.validateBalanced();
        ledgerTransactionRepository.save(ledger);

        //송금 완료
        transfer.success();
        return transfer;
    }

    @Transactional
    public Transfer transferWithFailure(Long fromAccountId, Long toAccountId, long amount){
        Account from = accountRepository.findById(fromAccountId).orElseThrow(()->new IllegalArgumentException("출금 계좌가 없습니다."));
        Account to = accountRepository.findById(toAccountId).orElseThrow(()->new IllegalArgumentException("입금 계좌가 없습니다."));

        Transfer transfer = new Transfer(from, to, amount);
        transferRepository.save(transfer);

        from.withdraw(amount);
        accountRepository.save(from);

        //출금이 DB에 저장된 직후 장애 발생
        throw new RuntimeException("송금 중 강제 장애");

        //실행되지 않음
        //to.deposit(amount);
        //accountRepository.save(to);
    }

    @Transactional
    public void transferWithDeadlockRisk(Long fromAccountId, Long toAccountId, long amount){
        Account from = accountRepository.findByIdForUpdate(fromAccountId).orElseThrow(()-> new IllegalArgumentException("출금 계좌가 없습니다."));

        //두 트랜젝션이 각각 첫번째 계좌 Lock을 잡도록
        //실험 목적으로 지연
        try{
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        Account to = accountRepository.findByIdForUpdate(toAccountId).orElseThrow(()-> new IllegalArgumentException("입금 계좌가 없습니다"));

         from.withdraw(amount);
         to.deposit(amount);
    }

    @Transactional
    public void transferWithLockOrdering(Long fromAccountId, Long toAccountId, long amount){
        if(fromAccountId.equals(toAccountId)){
            throw new IllegalArgumentException("같은 계좌로 송금할 수 없습니다.");
        }

        Long firstLockId = Math.min(fromAccountId, toAccountId);
        Long secondLockId = Math.max(fromAccountId, toAccountId);

        Account first = accountRepository.findByIdForUpdate(firstLockId).orElseThrow(()-> new IllegalArgumentException("계좌가 없습니다."));
        Account second = accountRepository.findByIdForUpdate(secondLockId).orElseThrow(()-> new IllegalArgumentException("계좌가 없습니다."));

        Account to = toAccountId.equals(first.getId())? first : second;
        Account from = fromAccountId.equals(first.getId())? first : second;

        from.withdraw(amount);
        to.deposit(amount);
    }

    @Transactional
    public void transferWithLedgerFailure(Long fromAccountId, Long toAccountId, long amount){
        Account from = accountRepository.findById(fromAccountId)
                .orElseThrow();

        Account to = accountRepository.findById(toAccountId)
                .orElseThrow();

        Transfer transfer = new Transfer(from, to ,amount);

        transferRepository.save(transfer);

        from.withdraw(amount);
        to.deposit(amount);

        LedgerTransaction ledger = LedgerTransaction.forTransfer(transfer, LocalDateTime.now());

        ledger.addEntry(from, -amount);
        ledger.addEntry(to, amount);

        ledger.validateBalanced();

        ledgerTransactionRepository.save(ledger);

        //장애 주입
        throw new RuntimeException("원장 기록 이후 강제 장애");
    }
}
