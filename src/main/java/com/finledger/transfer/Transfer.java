package com.finledger.transfer;

import com.finledger.account.Account;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    protected Transfer(){}

    public Transfer(Account fromAccount, Account toAccount, long amount){
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.status = TransferStatus.PENDING;
    }

    public void reverse(){
        if(status != TransferStatus.SUCCESS){
            throw new IllegalStateException("성공한 송금만 취소할 수 있습니다.");
        }
        status = TransferStatus.REVERSED;
    }

    public void success(){
        this.status = TransferStatus.SUCCESS;
    }

    public void fail(){
        this.status = TransferStatus.FAILED;
    }
}
