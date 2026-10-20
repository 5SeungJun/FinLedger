package com.finledger.externalbank;


import io.github.resilience4j.retry.Retry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class HttpExternalBankGateway implements ExternalBankGateway {
    private final RestClient restClient;
    private final Retry bankStatusRetry;

    public HttpExternalBankGateway(@Qualifier("externalBankRestClient") RestClient restClient, @Qualifier("bankStatusRetry") Retry bankStatusRetry){
        this.restClient = restClient;
        this.bankStatusRetry = bankStatusRetry;
    }

    @Override
    public BankTransferResponse submit(BankTransferRequest request, String scenario){
        return restClient.post()
                .uri("/mock-bank/transfers")
                .header("X-Simulation-Mode", scenario)
                .body(request)
                .retrieve()
                .body(BankTransferResponse.class);
    }

    @Override
    public Optional<BankTransferResponse> status(String requestId){
        try{
            return Retry.decorateSupplier(bankStatusRetry, ()->Optional.ofNullable(
                    restClient.get().uri("/mock-bank/transfers/{requestId}", requestId)
                            .retrieve()
                            .body(BankTransferResponse.class)
            )).get();
        } catch (HttpClientErrorException.NotFound e){
            return Optional.empty();
        }
    }
}
