package com.finledger.idempotency;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.ledger.LedgerTransactionRepository;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import com.finledger.transfer.Transfer;
import com.finledger.transfer.TransferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class IdempotencyFailureTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    IdempotentTransferService idempotentTransferService;

    private record Accounts(Long fromId, Long toId) {}

    private Accounts createAccounts(long initialBalance) {
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        if (initialBalance > 0) {
            from.deposit(initialBalance);
            accountRepository.save(from);
        }

        return new Accounts(from.getId(), to.getId());
    }

    @Test
    @DisplayName("실패한 송금은 롤백되며 같은 키로 재시도할 수 있다")
    void retryAfterFailure(){
        Accounts accounts = createAccounts(10_000L);
        String key = UUID.randomUUID().toString();

        long transferCountBefore = transferRepository.count();
        long ledgerCountBefore = ledgerTransactionRepository.count();

        //잔액 부족으로 첫 번째 송금 실패
        assertThatThrownBy(()->idempotentTransferService.transfer(key, accounts.fromId, accounts.toId, 30_000L))
                .isInstanceOf(RuntimeException.class);

        //실패한 요청의 멱등성 기록도 롤백됐는지 확인
        assertThat(idempotencyRecordRepository.findByKey(key))
                .isEmpty();

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(ledgerCountBefore);

        //부족한 잔액 충전
        Account from = accountRepository.findById(accounts.fromId())
                .orElseThrow();
        from.deposit(30_000L);
        accountRepository.save(from);

        //같은 키와 요청으로 재시도
        Transfer result = idempotentTransferService.transfer(key, accounts.fromId(), accounts.toId(), 30_000L);

        //최종 DB 값 검증
        Account resultFrom = accountRepository.findById(accounts.fromId()).orElseThrow();
        Account resultTo = accountRepository.findById(accounts.toId()).orElseThrow();

        assertThat(result.getId()).isNotNull();

        assertThat(resultFrom.getBalance())
                .isEqualTo(10_000L);

        assertThat(resultTo.getBalance())
                .isEqualTo(30_000L);

        assertThat(idempotencyRecordRepository.findByKey(key))
                .isPresent();

        assertThat(transferRepository.count())
                .isEqualTo(transferCountBefore + 1);

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(ledgerCountBefore + 1);
    }

    @Test
    @DisplayName("송금 성공 후 응답을 받지 못해도 재시도 시 중복 출금되지 않는다")
    void retryAfterLostResponse() {

        Accounts accounts = createAccounts(100_000L);
        String key = UUID.randomUUID().toString();

        long transferCountBefore = transferRepository.count();
        long ledgerCountBefore = ledgerTransactionRepository.count();

        //첫 번째 송금은 성공했지만 반환값을 사용하지 않음
        idempotentTransferService.transfer(key, accounts.fromId(), accounts.toId(), 30_000L);

        //클라이언트가 응답을 못 받았다고 가정하고 재전송
        Transfer retried = idempotentTransferService.transfer(key, accounts.fromId(), accounts.toId(), 30_000L);

        IdempotencyRecord record = idempotencyRecordRepository.findByKey(key)
                .orElseThrow();

        Account resultFrom = accountRepository.findById(accounts.fromId())
                .orElseThrow();

        Account resultTo = accountRepository.findById(accounts.toId())
                .orElseThrow();

        //새 거래가 아니라 기존 거래를 반환
        assertThat(retried.getId()).isEqualTo(record.getTransfer().getId());

        assertThat(transferRepository.count())
                .isEqualTo(transferCountBefore + 1);

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(ledgerCountBefore + 1);

        assertThat(resultFrom.getBalance())
                .isEqualTo(70_000L);

        assertThat(resultTo.getBalance())
                .isEqualTo(30_000L);
    }

    private record Attempt(Transfer transfer, Exception error) {}

    private Attempt attemptTransfer(CountDownLatch latch, String key, Accounts accounts, long amount) {
        try {
            latch.await();
            Transfer transfer = idempotentTransferService.transfer(key, accounts.fromId(), accounts.toId(), amount);

            return new Attempt(transfer, null);

        } catch (Exception e) {
            return new Attempt(null, e);
        }
    }

    @Test
    @DisplayName("동일 키의 서로 다른 송금 요청은 하나만 성공한다")
    void concurrentDifferentRequests() throws Exception {

        Accounts accounts = createAccounts(100_000L);
        String key = UUID.randomUUID().toString();

        long transferCountBefore = transferRepository.count();
        long ledgerCountBefore = ledgerTransactionRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch latch = new CountDownLatch(1);

        try {
            Future<Attempt> future1 = executor.submit(() -> attemptTransfer(latch, key, accounts, 30_000L)
            );

            Future<Attempt> future2 = executor.submit(() -> attemptTransfer(latch, key, accounts, 40_000L)
            );

            latch.countDown();

            Attempt first = future1.get(15, TimeUnit.SECONDS);
            Attempt second = future2.get(15, TimeUnit.SECONDS);

            int successCount = (first.transfer() != null ? 1 : 0) + (second.transfer() != null ? 1 : 0);
            assertThat(successCount).isEqualTo(1);

            Exception rejected = first.error() != null ? first.error() : second.error();
            assertThat(rejected).isInstanceOf(IdempotencyKeyConflictException.class);

            long successfulAmount = first.transfer() != null ? 30_000L : 40_000L;
            Account resultFrom = accountRepository.findById(accounts.fromId()).orElseThrow();
            Account resultTo = accountRepository.findById(accounts.toId()).orElseThrow();
            assertThat(resultFrom.getBalance()).isEqualTo(100_000L - successfulAmount);
            assertThat(resultTo.getBalance()).isEqualTo(successfulAmount);

            assertThat(transferRepository.count()).isEqualTo(transferCountBefore + 1);
            assertThat(ledgerTransactionRepository.count()).isEqualTo(ledgerCountBefore + 1);

        } finally {
            executor.shutdownNow();
        }
    }
}