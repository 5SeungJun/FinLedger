package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class TransferTest {
    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    TransferService transferService;

    @Test
    @DisplayName("정상적으로 계좌이체한다.")
    void transferTest(){
        //given
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        long beforeTotal = from.getBalance() + to.getBalance();

        //when
        Transfer transfer = transferService.transfer(from.getId(), to.getId(), 30_000L);

        //then
        Account resultFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account resultTo = accountRepository.findById(to.getId()).orElseThrow();

        assertThat(resultFrom.getBalance()).isEqualTo(70_000L);
        assertThat(resultTo.getBalance()).isEqualTo(30_000L);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SUCCESS);

        long afterTotal = resultFrom.getBalance() + resultTo.getBalance();

        assertThat(afterTotal).isEqualTo(beforeTotal);
    }
}
