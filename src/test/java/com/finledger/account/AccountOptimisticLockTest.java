package com.finledger.account;

import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(AccountOptimisticLockTest.OptimisticRaceWorker.class)
class AccountOptimisticLockTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    OptimisticRaceWorker worker;

    @Test
    @DisplayName("낙관적 락으로 동시 출금을 감지한다")
    void OptimisticTest() throws Exception {
        // given
        Member member = memberRepository.save(new Member("sj"));
        Account account = accountRepository.save(new Account(member));

        account.deposit(10_000L);
        accountRepository.save(account);

        Long accountId = account.getId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<String> task = () -> {
            try {
                worker.withdrawAfterBothRead(accountId, 8_000L, barrier);
                return "SUCCESS";
            } catch (OptimisticLockingFailureException e) {
                return "CONFLICT";
            }
        };

        long start = System.nanoTime();

        try {
            // when
            Future<String> first = executor.submit(task);
            Future<String> second = executor.submit(task);

            String result1 = first.get(15, TimeUnit.SECONDS);
            String result2 = second.get(15, TimeUnit.SECONDS);

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - start
            );

            long successCount = (result1.equals("SUCCESS") ? 1 : 0) + (result2.equals("SUCCESS") ? 1 : 0);
            long conflictCount = (result1.equals("CONFLICT") ? 1 : 0) + (result2.equals("CONFLICT") ? 1 : 0);

            Account result = accountRepository
                    .findById(accountId)
                    .orElseThrow();

            System.out.println("성공: " + successCount);
            System.out.println("충돌: " + conflictCount);
            System.out.println("잔액: " + result.getBalance());
            System.out.println("실행시간: " + elapsedMs + "ms");

            // then
            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(result.getBalance()).isEqualTo(2_000L);

        } finally {
            executor.shutdownNow();
        }
    }

    // 동시 충돌 재현만을 위한 테스트 전용 Bean
    public static class OptimisticRaceWorker {

        private final AccountRepository accountRepository;

        public OptimisticRaceWorker(AccountRepository accountRepository) {
            this.accountRepository = accountRepository;
        }

        @Transactional
        public void withdrawAfterBothRead(Long accountId, long amount, CyclicBarrier barrier) {
            Account account = accountRepository
                    .findById(accountId)
                    .orElseThrow();

            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException(e);
            }

            account.withdraw(amount);

            // 커밋 전 SQL을 강제로 실행해 충돌을 감지
            accountRepository.flush();
        }
    }
}