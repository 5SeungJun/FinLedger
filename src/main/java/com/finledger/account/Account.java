package com.finledger.account;

import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import com.finledger.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(name = "uk_account_number", columnNames = "account_number"))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    private long balance;

    private Instant createdAt;

    protected Account() {
    }

    public Account(Member member, String accountNumber, long initialBalance) {
        this.member = Objects.requireNonNull(member);
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        if (initialBalance < 0) {
            throw new BusinessException(ErrorCode.INVALID_INITIAL_BALANCE);
        }
        this.accountNumber = accountNumber.trim();
        this.balance = initialBalance;
        this.createdAt = Instant.now();
    }

    public void deposit(long amount) {
        validatePositiveAmount(amount);
        balance += amount;
    }

    public void withdraw(long amount) {
        validatePositiveAmount(amount);
        if (balance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        balance -= amount;
    }

    private void validatePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public long getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
