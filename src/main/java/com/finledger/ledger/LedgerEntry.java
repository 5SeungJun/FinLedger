package com.finledger.ledger;

import com.finledger.account.Account;
import jakarta.persistence.*;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_transaction_id")
    private LedgerTransaction ledgerTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private long amount;

    protected LedgerEntry() {
    }

    LedgerEntry(LedgerTransaction ledgerTransaction, Account account, long amount) {
        this.ledgerTransaction = ledgerTransaction;
        this.account = account;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public long getAmount() {
        return amount;
    }
}