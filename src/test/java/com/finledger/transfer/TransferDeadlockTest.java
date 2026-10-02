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
public class TransferDeadlockTest {
    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferService transferService;

    @Test
    @DisplayName("서로 다른 순서로 Lock을 획득하면 Deadlock이 발생할 수 있다.")
    void deadlockTest() throws Exception{
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

                transferService.transferWithDeadlockRisk(accountA.getId(), accountB.getId(), 10_000L);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("A -> B 실패: " + e.getClass().getName());
            }
        };

        Runnable transferBtoA = () -> {
            try {
                startLatch.await();

                transferService.transferWithDeadlockRisk(accountB.getId(), accountA.getId(), 20_000L);
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
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("성공 :" + successCount.get());
        System.out.println("실패 : " + failureCount.get());

        assertThat(failureCount.get()).isGreaterThanOrEqualTo(1);
    }
}
