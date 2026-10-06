package com.finledger.ledger;

import com.finledger.account.Account;
import com.finledger.transfer.Transfer;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "ledgerTransaction", cascade = CascadeType.ALL)
    private List<LedgerEntry> entries = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, unique = true)
    private Transfer transfer;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected LedgerTransaction(){}

    public LedgerTransaction(Transfer transfer, LocalDateTime createdAt){
        this.transfer = transfer;
        this.createdAt = createdAt;
    }

    public void addEntry(Account account,long amount){
        LedgerEntry entry = new LedgerEntry(this, account, amount);

        entries.add(entry);
    }

    public long getTotalAmount(){
        return entries.stream()
                .mapToLong(LedgerEntry::getAmount)
                .sum();
    }

    public Long getId(){
        return id;
    }

    public Transfer getTransfer(){
        return transfer;
    }

    public List<LedgerEntry> getEntries(){
        return Collections.unmodifiableList(entries);
    }

    public LocalDateTime getCreateAt(){
        return createdAt;
    }

    public void validateBalanced(){
        if(getTotalAmount() != 0L){
            throw new IllegalStateException("원장의 합계는 0이어야 합니다.");
        }
    }
}
