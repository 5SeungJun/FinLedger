package com.finledger.account;

import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import com.finledger.member.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private final Member member = new Member("홍길동");

    @Test
    void depositsPositiveAmount() {
        Account account = new Account(member, "100-001", 10_000L);

        account.deposit(5_000L);

        assertThat(account.getBalance()).isEqualTo(15_000L);
    }

    @Test
    void withdrawsWhenBalanceIsSufficient() {
        Account account = new Account(member, "100-001", 10_000L);

        account.withdraw(4_000L);

        assertThat(account.getBalance()).isEqualTo(6_000L);
    }

    @Test
    void rejectsWithdrawalWhenBalanceIsInsufficient() {
        Account account = new Account(member, "100-001", 10_000L);

        assertThatThrownBy(() -> account.withdraw(20_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(account.getBalance()).isEqualTo(10_000L);
    }

    @Test
    void rejectsNonPositiveTransactionAmount() {
        Account account = new Account(member, "100-001", 10_000L);

        assertThatThrownBy(() -> account.deposit(0L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void rejectsNegativeInitialBalance() {
        assertThatThrownBy(() -> new Account(member, "100-001", -1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INITIAL_BALANCE);
    }
}
