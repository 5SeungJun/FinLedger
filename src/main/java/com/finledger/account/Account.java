package com.finledger.account;

import com.finledger.member.Member;
import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {

    //커밋 테스트
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private long version;

    protected Account(){}

    public Account(Member member){
        this.member = member;
        this.balance = 0L;
    }

    public void deposit(long amount) {
        if(amount <= 0){
            throw new IllegalArgumentException(
                    "입금 금액은 1원 이상이어야 합니다."
            );
        }
        balance += amount;
    }

    public void withdraw(long amount){
        if(amount <= 0){
            throw new IllegalArgumentException(
                    "출금 금액은 1원 이상이어야 합니다."
            );
        }
        if(balance < amount){
            throw new IllegalStateException(
                    "잔액이 부족합니다."
            );
        }
        balance -= amount;
    }

    public Long getId(){
        return id;
    }

    public long getBalance(){
        return balance;
    }
}
