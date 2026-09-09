package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    private Account toAccount;

    private long amount;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    private Instant createdAt;

    private Instant completedAt;

    protected Transfer() {
    }

    public Transfer(Account fromAccount, Account toAccount, long amount) {
        this.fromAccount = Objects.requireNonNull(fromAccount);
        this.toAccount = Objects.requireNonNull(toAccount);
        if (fromAccount == toAccount) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
        this.amount = amount;
        this.status = TransferStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void complete() {
        if (status != TransferStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER_STATUS);
        }
        this.status = TransferStatus.SUCCESS;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public long getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
