package com.finledger.integration;

import com.finledger.account.Account;
import com.finledger.account.AccountRepository;
import com.finledger.idempotency.IdempotentTransferService;
import com.finledger.ledger.LedgerTransaction;
import com.finledger.ledger.LedgerTransactionRepository;
import com.finledger.ledger.ReversalService;
import com.finledger.member.Member;
import com.finledger.member.MemberRepository;
import com.finledger.reconciliation.ReconciliationService;
import com.finledger.transfer.Transfer;
import com.finledger.transfer.TransferRepository;
import com.finledger.transfer.TransferService;
import com.finledger.transfer.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class FinancialFlowIntegrationTest {
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    TransferRepository transferRepository;
    @Autowired
    LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired
    IdempotentTransferService idempotentTransferService;
    @Autowired
    ReconciliationService reconciliationService;
    @Autowired
    ReversalService reversalService;

    @Test
    @DisplayName("송금부터 역거래까지 금융 정합성을 유지한다")
    void fullFinancialFlow() {

        //given
        Member member = memberRepository.save(new Member("sj"));
        Account a = accountRepository.save(new Account(member));
        Account b = accountRepository.save(new Account(member));

        a.deposit(100_000L);
        accountRepository.save(a);

        String key = UUID.randomUUID().toString();

        long transferCountBefore = transferRepository.count();
        long ledgerCountBefore = ledgerTransactionRepository.count();

        //정상 송금
        Transfer first = idempotentTransferService.transfer(key, a.getId(), b.getId(), 30_000L);

        //동일 요청 재전송
        Transfer duplicate = idempotentTransferService.transfer(key, a.getId(), b.getId(), 30_000L);

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(transferRepository.count()).isEqualTo(transferCountBefore + 1);

        //원본 원장 확인
        LedgerTransaction original = ledgerTransactionRepository.findByTransferId(first.getId())
                .orElseThrow();

        assertThat(original.getEntries()).hasSize(2);
        assertThat(original.getTotalAmount()).isZero();

        //송금 직후 대사
        assertThat(reconciliationService.reconcile(a.getId(), 100_000L).matched()).isTrue();

        assertThat(reconciliationService.reconcile(b.getId(), 0L).matched()).isTrue();

        //역거래 수행
        reversalService.reverse(first.getId());

        //역거래 원장 확인
        LedgerTransaction reversal = ledgerTransactionRepository.findByReversalOfId(original.getId()).orElseThrow();

        assertThat(reversal.getEntries()).hasSize(2);
        assertThat(reversal.getTotalAmount()).isZero();

        //원 거래와 역거래의 Entry 합계
        assertThat(original.getTotalAmount() + reversal.getTotalAmount()).isZero();

        //최종 잔액 확인
        Account resultA = accountRepository.findById(a.getId()).orElseThrow();
        Account resultB = accountRepository.findById(b.getId()).orElseThrow();

        assertThat(resultA.getBalance()).isEqualTo(100_000L);
        assertThat(resultB.getBalance()).isZero();
        assertThat(resultA.getBalance() + resultB.getBalance()).isEqualTo(100_000L);

        //역거래 후 원장 대사
        assertThat(reconciliationService.reconcile(a.getId(), 100_000L).matched()).isTrue();
        assertThat(reconciliationService.reconcile(b.getId(), 0L).matched()).isTrue();

        //거래 상태와 원장 개수 확인
        Transfer resultTransfer = transferRepository.findById(first.getId()).orElseThrow();

        assertThat(resultTransfer.getStatus()).isEqualTo(TransferStatus.REVERSED);
        assertThat(transferRepository.count()).isEqualTo(transferCountBefore + 1);

        //원 거래 1건 + 역 거래 1건
        assertThat(ledgerTransactionRepository.count()).isEqualTo(ledgerCountBefore + 2);

        //중복 역거래 차단
        assertThatThrownBy(() -> reversalService.reverse(first.getId())).isInstanceOf(IllegalStateException.class);
    }


}