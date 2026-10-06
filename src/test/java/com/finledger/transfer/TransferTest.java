package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.ledger.LedgerTransaction;
import com.finledger.ledger.LedgerTransactionRepository;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    LedgerTransactionRepository ledgerTransactionRepository;

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

    @Disabled
    @Test
    @DisplayName("트랜잭션이 없으면 송금 중 장애 발생 시 출금만 반영된다.")
    void transferFailureTest(){
        //given
        Member member =memberRepository.save(new Member("sj"));

        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        long beforeTotal = from.getBalance() + to.getBalance();

        //when
        assertThatThrownBy(() ->
                transferService.transferWithFailure(from.getId(), to.getId(), 30_000L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("송금 중 강제 장애");

        //then
        Account resultFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account resultTo = accountRepository.findById((to.getId())).orElseThrow();

        long afterTotal = resultFrom.getBalance() + resultTo.getBalance();

        assertThat(resultFrom.getBalance()).isEqualTo(70_000L);
        assertThat(resultTo.getBalance()).isZero();
        assertThat(afterTotal).isEqualTo(70_000L);
        assertThat(afterTotal).isNotEqualTo(beforeTotal);
    }

    @Test
    @DisplayName("송금 중 장애 발생 시 전체 작업을 롤백한다")
    void transferFailureRollback(){
        //given
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        long beforeTotal = from.getBalance() + to.getBalance();
        long transferCountBefore = transferRepository.count();

        //when
        assertThatThrownBy(()->
                transferService.transferWithFailure(from.getId(), to.getId(), 30_000L)
                ).isInstanceOf(RuntimeException.class);

        //then
        Account resultFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account resultTo = accountRepository.findById(to.getId()).orElseThrow();

        assertThat(resultFrom.getBalance()).isEqualTo(100_000L);
        assertThat(resultTo.getBalance()).isZero();

        long afterTotal = resultFrom.getBalance() + resultTo.getBalance();

        assertThat(afterTotal).isEqualTo(beforeTotal);

        assertThat(transferRepository.count()).isEqualTo(transferCountBefore);
    }

    @Test
    @DisplayName("송금 성공 시 잔액 변경과 원장이 함께 기록된다.")
    void transferWithLedger(){

        //given
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        //when
        Transfer transfer = transferService.transfer(from.getId(), to.getId(), 30_000L);

        //then
        Account resultFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account resultTo = accountRepository.findById(to.getId()).orElseThrow();

        LedgerTransaction ledger = ledgerTransactionRepository.findByTransferId(transfer.getId())
                        .orElseThrow();

        assertThat(resultFrom.getBalance())
                .isEqualTo(70_000L);

        assertThat((resultTo.getBalance()))
                .isEqualTo(30_000L);

        assertThat(ledger.getEntries())
                .hasSize(2);

        assertThat(ledger.getTotalAmount())
                .isZero();

    }
}
