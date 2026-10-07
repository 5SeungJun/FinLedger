package com.finledger.reconciliation;

public record ReconciliationResult (
    Long accountId,
    long baseBalance, //Ledger 기록을 시작하기 전 기준 잔액
    long ledgerDelta, //이후 LedgerEntry 합계
    long expectedBalance, //baseBalance + ledgerDelta
    long actualBalance, //현재 Account.balance
    long difference, //actual - expected
    boolean matched //둘이 같은지
){
}
