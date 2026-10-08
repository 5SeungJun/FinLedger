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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
public class IdempotentTransferServiceTest {
    @Autowired
    MemberRepository memberRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    IdempotentTransferService idempotentTransferService;

    @Autowired
    LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    @DisplayName("같은 멱등성 키의 동일 요청은 한 번만 송금된다.")
    void duplicateRequestIsProcessedOnce(){
        //given
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        String key = "transfer-test-001" + UUID.randomUUID();

        long transferCountBefore = transferRepository.count();
        long ledgerCountBefore = ledgerTransactionRepository.count();

        //when
        Transfer first = idempotentTransferService.transfer(key, from.getId(), to.getId(), 30_000L);
        Transfer second = idempotentTransferService.transfer(key, from.getId(), to.getId(), 30_000L);

        //then
        Account resultFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account resultTo = accountRepository.findById(to.getId()).orElseThrow();

        assertThat(first.getId())
                .isEqualTo(second.getId());

        assertThat(resultFrom.getBalance())
                .isEqualTo(70_000L);

        assertThat(resultTo.getBalance())
                .isEqualTo(30_000L);

        assertThat(transferRepository.count())
                .isEqualTo(transferCountBefore + 1);

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(ledgerCountBefore + 1);
    }

    @Test
    @DisplayName("같은 멱등성 키를 다른 요청에 재사용할 수 없다.")
    void sameKeyDifferentRequestFails(){
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        String key = "transfer-test-002" + UUID.randomUUID();

        idempotentTransferService.transfer(key, from.getId(), to.getId(), 30_000L);

        assertThatThrownBy(() -> idempotentTransferService.transfer(key, from.getId(), to.getId(), 50_000L))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("동시에 같은 멱등성 요청이 들어와도 한 번만 송금된다")
    void concurrentDuplicateRequest() throws Exception{
        Member member = memberRepository.save(new Member("sj"));
        Account from = accountRepository.save(new Account(member));
        Account to = accountRepository.save(new Account(member));

        from.deposit(100_000L);
        accountRepository.save(from);

        String key = "transfer-concurrent-001" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch latch = new CountDownLatch(1);

        Callable<Transfer> task = () -> {
            latch.await();

            return idempotentTransferService.transfer(key, from.getId(), to.getId(), 30_000L);
        };

        Future<Transfer> first = executor.submit(task);
        Future<Transfer> second = executor.submit(task);

        latch.countDown();

        Transfer result1 = first.get(10, TimeUnit.SECONDS);
        Transfer result2 = second.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        Account resultFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account resultTo = accountRepository.findById(to.getId()).orElseThrow();

        assertThat(result1.getId()).isEqualTo(result2.getId());
        assertThat(resultFrom.getBalance()).isEqualTo(70_000L);
        assertThat(resultTo.getBalance()).isEqualTo(30_000L);
    }
}
