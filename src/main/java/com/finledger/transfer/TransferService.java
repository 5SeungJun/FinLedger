package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository){
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
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

        Account from = accountRepository.findById(fromAccountId).orElseThrow(()-> new IllegalArgumentException("출금 계좌가 없습니다."));
        Account to = accountRepository.findById(toAccountId).orElseThrow(()-> new IllegalArgumentException("입금 계좌가 없습니다."));

        Transfer transfer = new Transfer(from, to, amount);

        transferRepository.save(transfer);

        from.withdraw(amount);

        to.deposit(amount);

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
}
