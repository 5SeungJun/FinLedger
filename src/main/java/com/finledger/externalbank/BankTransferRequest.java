package com.finledger.externalbank;

public record BankTransferRequest(
        String requestId,
        String fromAccount,
        String toAccount,
        long amount
)
{}
