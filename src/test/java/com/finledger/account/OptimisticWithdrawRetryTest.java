package com.finledger.account;

import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OptimisticWithdrawRetryTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    OptimisticWithdrawRetryService retryService;

    @Test
    @DisplayName("충돌이 나도 재시도로 모든 동시 출금이 성공한다")
    void retryResolvesConflicts() throws Exception {
        // given
        Long accountId = createAccount(10_000L);

        // when
        List<String> results = withdrawConcurrently(3, accountId, 3_000L);

        // then
        assertThat(results).containsExactly("SUCCESS", "SUCCESS", "SUCCESS");
        assertThat(balanceOf(accountId)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("잔액 부족은 재시도하지 않고 그대로 실패한다")
    void insufficientBalanceIsNotRetried() throws Exception {
        // given
        Long accountId = createAccount(10_000L);

        // when
        List<String> results = withdrawConcurrently(2, accountId, 8_000L);

        // then
        assertThat(results).containsExactlyInAnyOrder("SUCCESS", "INSUFFICIENT");
        assertThat(balanceOf(accountId)).isEqualTo(2_000L);
    }

    private Long createAccount(long deposit) {
        Member member = memberRepository.save(new Member("sj"));
        Account account = accountRepository.save(new Account(member));

        account.deposit(deposit);
        accountRepository.save(account);

        return account.getId();
    }

    private long balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private List<String> withdrawConcurrently(int threads, Long accountId, long amount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();   // 모든 스레드가 준비되면 동시에 출발

                    try {
                        retryService.withdrawWithRetry(accountId, amount);
                        return "SUCCESS";
                    } catch (OptimisticLockingFailureException e) {
                        return "CONFLICT_EXHAUSTED";   // 재시도를 모두 소진
                    } catch (IllegalStateException e) {
                        return "INSUFFICIENT";         // 잔액 부족 (비즈니스 예외)
                    }
                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}