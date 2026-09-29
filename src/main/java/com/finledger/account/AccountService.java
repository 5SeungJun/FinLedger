package com.finledger.account;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void withdraw(Long accountId, long amount){
        Account account = accountRepository.findById(accountId).orElseThrow(()-> new IllegalArgumentException("계좌가 없습니다."));

        account.withdraw(amount);
    }

    @Transactional
    public void withdrawWithDelay(Long accountId, long amount){
        Account account = accountRepository.findById(accountId).orElseThrow();

        try{
            Thread.sleep(500); //두 트랜잭션이 같은 balance를 읽게 만드는 실험용 Fault Injection
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        account.withdraw(amount);
    }
}
