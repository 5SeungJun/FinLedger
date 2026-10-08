package com.finledger.idempotency;

import com.finledger.transfer.Transfer;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
})
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Long fromAccountId;

    @Column(nullable = false, updatable = false)
    private Long toAccountId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", unique = true)
    private Transfer transfer;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord(){}

    public IdempotencyRecord(String idempotencyKey, Long fromAccountId, Long toAccountId, long amount){
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    public boolean matches(Long fromAccountId, Long toAccountId, long amount){
        return this.fromAccountId.equals(fromAccountId) && this.toAccountId.equals(toAccountId) && this.amount == amount;
    }

    public void complete(Transfer transfer){
        this.transfer = transfer;
    }

    public String getIdempotencyKey(){
        return idempotencyKey;
    }

    public Transfer getTransfer(){
        return transfer;
    }


}
