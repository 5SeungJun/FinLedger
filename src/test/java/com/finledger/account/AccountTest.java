package com.finledger.account;

import com.finledger.account.Account;
import com.finledger.member.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {
    @Test
    void depositTest(){
        Member member = new Member("sj");
        Account account = new Account(member);

        account.deposit(100_000L);

        assertThat(account.getBalance()).isEqualTo(100_000L);
    }

    @Test
    void withdrawTest(){
        Member member = new Member("sj");
        Account account = new Account(member);

        account.deposit(100_000L);
        account.withdraw(30_000L);

        assertThat(account.getBalance()).isEqualTo(70_000L);
    }
}
