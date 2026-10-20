package com.finledger.externalbank;

public record BankTransferResponse(
        String requestId,
        String status,
        long amount
) {
}
