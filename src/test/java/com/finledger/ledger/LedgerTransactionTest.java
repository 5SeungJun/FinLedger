package com.finledger.ledger;

import com.finledger.account.Account;
import com.finledger.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class LedgerTransactionTest {
    @Test
    @DisplayName("내부 송금의 원장 Entry 합계는 0이다")
    void LedgerTest(){
        //given
        Member member = new Member("sj");
        Account from = new Account(member);
        Account to = new Account(member);

        LedgerTransaction ledger = new LedgerTransaction(LocalDateTime.now());

        //when
        ledger.addEntry(from, -30_000L);
        ledger.addEntry(to, 30_000L);

        //then
        assertThat(ledger.getEntries()).hasSize(2);
        assertThat(ledger.getTotalAmount()).isZero();

    }
}
