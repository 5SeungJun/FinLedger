package com.finledger.account;

import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AccountConcurrencyTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountService accountService;

    @Test
    @DisplayName("동시에 출금하면 Lost Update가 발생한다")
    void concurrencyTest() throws Exception {
        // given
        Member member = memberRepository.save(new Member("sj"));
        Account account = accountRepository.save(new Account(member));

        account.deposit(10_000L);
        accountRepository.save(account);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();

        // when
        Runnable task = () -> {
            try {
                startLatch.await();

                accountService.withdrawWithDelay(account.getId(), 8_000L);

                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        executor.submit(task);
        executor.submit(task);

        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // then
        Account result = accountRepository.findById(account.getId()).orElseThrow();

        System.out.println("성공한 출금 요청 = " + successCount.get());
        System.out.println("최종 잔액 = " + result.getBalance());

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(result.getBalance()).isEqualTo(2_000L);
    }
}