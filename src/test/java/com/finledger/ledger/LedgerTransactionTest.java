package com.finledger.ledger;

import com.finledger.account.Account;
import com.finledger.member.Member;
import com.finledger.transfer.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class LedgerTransactionTest {
    @Test
    @DisplayName("내부 송금의 원장 Entry 합계는 0이다")
    void LedgerTest(){
        //given
        Member member = new Member("sj");
        Account from = new Account(member);
        Account to = new Account(member);

        LedgerTransaction ledger = new LedgerTransaction(new Transfer(from, to, 30_000L), LocalDateTime.now());

        //when
        ledger.addEntry(from, -30_000L);
        ledger.addEntry(to, 30_000L);

        //then
        assertThat(ledger.getEntries()).hasSize(2);
        assertThat(ledger.getTotalAmount()).isZero();

    }

    @Test
    @DisplayName("원장 항목의 합계가 0이 아니면 검증시 예외가 발생한다")
    void unbalancedLedgerTest(){
        //given
        Member member = new Member("sj");
        Account from = new Account(member);
        Account to = new Account(member);

        LedgerTransaction ledger = new LedgerTransaction(new Transfer(from, to, 30_000L), LocalDateTime.now());

        //when
        ledger.addEntry(from, -30_000L);
        ledger.addEntry(to, 20_000L);

        //then
        assertThatThrownBy(ledger::validateBalanced)
                .isInstanceOf(IllegalStateException.class).hasMessage("원장의 합계는 0이어야 합니다.");

    }
}
