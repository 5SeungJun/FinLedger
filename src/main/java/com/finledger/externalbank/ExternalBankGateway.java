package com.finledger.externalbank;

import java.util.Optional;

public interface ExternalBankGateway {
    BankTransferResponse submit(BankTransferRequest request, String scenario);

    Optional<BankTransferResponse> status(String requestId);
}
