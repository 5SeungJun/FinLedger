package com.finledger.account;

import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import com.finledger.member.Member;
import com.finledger.member.MemberService;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final MemberService memberService;

    public AccountService(AccountRepository accountRepository, MemberService memberService) {
        this.accountRepository = accountRepository;
        this.memberService = memberService;
    }

    public Account create(Long memberId, String accountNumber, long initialBalance) {
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACCOUNT_NUMBER);
        }
        Member member = memberService.get(memberId);
        return accountRepository.save(new Account(member, accountNumber, initialBalance));
    }

    public Account get(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    public Account deposit(Long accountId, long amount) {
        Account account = get(accountId);
        account.deposit(amount);
        return accountRepository.save(account);
    }

    public Account withdraw(Long accountId, long amount) {
        Account account = get(accountId);
        account.withdraw(amount);
        return accountRepository.save(account);
    }
}
