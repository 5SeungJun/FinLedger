package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import com.finledger.member.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private final Member member = new Member("홍길동");

    @Test
    void changesFromPendingToSuccess() {
        Account fromAccount = new Account(member, "100-001", 100_000L);
        Account toAccount = new Account(member, "100-002", 0L);
        Transfer transfer = new Transfer(fromAccount, toAccount, 30_000L);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getCompletedAt()).isNull();

        transfer.complete();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(transfer.getCompletedAt()).isNotNull();
    }

    @Test
    void rejectsNonPositiveAmount() {
        Account fromAccount = new Account(member, "100-001", 100_000L);
        Account toAccount = new Account(member, "100-002", 0L);

        assertThatThrownBy(() -> new Transfer(fromAccount, toAccount, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void rejectsTransferToSameAccount() {
        Account account = new Account(member, "100-001", 100_000L);

        assertThatThrownBy(() -> new Transfer(account, account, 10_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void cannotCompleteTwice() {
        Account fromAccount = new Account(member, "100-001", 100_000L);
        Account toAccount = new Account(member, "100-002", 0L);
        Transfer transfer = new Transfer(fromAccount, toAccount, 30_000L);
        transfer.complete();

        assertThatThrownBy(transfer::complete)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TRANSFER_STATUS);
    }
}
