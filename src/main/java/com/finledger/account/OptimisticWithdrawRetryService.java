package com.finledger.account;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class OptimisticWithdrawRetryService {
    private static final int MAX_ATTEMPTS = 3;
    private final AccountService accountService;

    public OptimisticWithdrawRetryService(AccountService accountService){
        this.accountService = accountService;
    }

    //외부 트랜잭션 없이 호출하는 실험용 진입점
    public void withdrawWithRetry(Long accountId, long amount){
        for(int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++){
            try{
                accountService.withdrawOptimistic(accountId, amount);
                return;
            } catch(OptimisticLockingFailureException e){
                if(attempt == MAX_ATTEMPTS){
                    throw e;
                }
            }
        }
    }
}
