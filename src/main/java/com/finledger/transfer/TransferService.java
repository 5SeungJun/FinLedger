package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    public Transfer transfer(Long fromAccountId, Long toAccountId, long amount) {
        if (Objects.equals(fromAccountId, toAccountId)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }

        Account fromAccount = getAccount(fromAccountId);
        Account toAccount = getAccount(toAccountId);

        // Validate insufficient balance before any transfer row is persisted.
        fromAccount.withdraw(amount);

        // Persist each step separately on purpose. EX-003 will observe this baseline without a service transaction.
        Transfer transfer = transferRepository.save(new Transfer(fromAccount, toAccount, amount));

        accountRepository.save(fromAccount);

        toAccount.deposit(amount);
        accountRepository.save(toAccount);

        transfer.complete();
        return transferRepository.save(transfer);
    }

    public Transfer get(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
    }

    public List<Transfer> findByAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return transferRepository.findByFromAccount_IdOrToAccount_IdOrderByCreatedAtDesc(accountId, accountId);
    }

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
