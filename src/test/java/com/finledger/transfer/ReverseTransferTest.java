package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.ledger.ReversalService;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
public class ReverseTransferTest {
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    TransferService transferService;
    @Autowired
    ReversalService reversalService;
    @Autowired
    TransferRepository transferRepository;

    @Test
    @DisplayName("성공한 송금을 역거래로 취소한다.")
    void reverseTransfer() {
        // given
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        Transfer transfer = transferService.transfer(from.getId(), to.getId(), 30_000L);

        // when
        reversalService.reverse(transfer.getId());

        // then
        Account resultFrom = accountRepository.findById(from.getId())
                .orElseThrow();

        Account resultTo = accountRepository.findById(to.getId())
                .orElseThrow();

        Transfer resultTransfer = transferRepository
                .findById(transfer.getId())
                .orElseThrow();

        assertThat(resultFrom.getBalance())
                .isEqualTo(100_000L);

        assertThat(resultTo.getBalance())
                .isZero();

        assertThat(resultTransfer.getStatus())
                .isEqualTo(TransferStatus.REVERSED);

        assertThat(resultFrom.getBalance() + resultTo.getBalance())
                .isEqualTo(100_000L);
    }

    @Test
    @DisplayName("같은 송금을 두 번 취소할 수 없다.")
    void duplicateReversal(){
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        Transfer transfer = transferService.transfer(from.getId(), to.getId(), 30_000L);

        reversalService.reverse(transfer.getId());

        assertThatThrownBy(()->reversalService.reverse(transfer.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 취소된 거래입니다.");
    }
}
