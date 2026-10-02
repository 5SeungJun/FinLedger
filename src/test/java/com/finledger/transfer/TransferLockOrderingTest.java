package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class TransferLockOrderingTest {
    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferService transferService;

    @Test
    @DisplayName("일관된 Lock 순서로 송금 Deadlock을 방지한다.")
    void lockOrderingTest() throws Exception{
        //given
        Member member = memberRepository.save(new Member("sj"));
        Account accountA = accountRepository.save(new Account(member));
        Account accountB = accountRepository.save(new Account(member));

        accountA.deposit(100_000L);
        accountB.deposit(100_000L);

        accountRepository.save(accountA);
        accountRepository.save(accountB);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        Runnable transferAtoB = () -> {
            try {
                startLatch.await();

                transferService.transferWithLockOrdering(accountA.getId(), accountB.getId(), 10_000L);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("A -> B 실패: " + e.getClass().getName());
            }
        };

        Runnable transferBtoA = () -> {
            try {
                startLatch.await();

                transferService.transferWithLockOrdering(accountB.getId(), accountA.getId(), 20_000L);
                successCount.incrementAndGet();
            } catch (Exception e ){
                failureCount.incrementAndGet();
                System.out.println("B -> A 실패 : " + e.getClass().getName());
            }
        };

        executor.submit(transferAtoB);
        executor.submit(transferBtoA);

        startLatch.countDown();
        executor.shutdown();

        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        Account resultA = accountRepository.findById(accountA.getId()).orElseThrow();

        Account resultB = accountRepository.findById(accountB.getId()).orElseThrow();

        System.out.println("성공 : " + successCount.get());
        System.out.println("실패 : " + failureCount.get());
        System.out.println("A 잔액 : " + resultA.getBalance());
        System.out.println("B 잔액 : " + resultB.getBalance());

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(failureCount.get()).isZero();

        assertThat(resultA.getBalance()).isEqualTo(110_000L);
        assertThat(resultB.getBalance()).isEqualTo(90_000L);

        assertThat(resultA.getBalance() + resultB.getBalance()).isEqualTo(200_000L);
    }
}
