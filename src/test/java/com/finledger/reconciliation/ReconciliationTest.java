package com.finledger.reconciliation;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import com.finledger.transfer.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class ReconciliationTest {
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    TransferService transferService;
    @Autowired
    ReconciliationService reconciliationService;

    @Test
    @DisplayName("계좌 잔액과 원장이 다르면 불일치를 탐지한다.")
    void reconciliationDetectMismatch(){
        //given
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        long baseBalance = 100_000L;

        transferService.transfer(from.getId(), to.getId(), 30_000L);

        //장애 주입 : balance 70,000 -> 60,000로 강제 변경
        accountRepository.forceUpdateBalanceForTest(from.getId(), 60_000L);

        //when
        ReconciliationResult result = reconciliationService.reconcile(from.getId(), baseBalance);

        //then
        assertThat(result.expectedBalance())
                .isEqualTo(70_000L);
        assertThat(result.actualBalance())
                .isEqualTo(60_000L);
        assertThat(result.difference())
                .isEqualTo(-10_000L);
        assertThat(result.matched())
                .isFalse();
    }
}
