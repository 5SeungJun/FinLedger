package com.finledger.externalbank;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

@Service
public class ExternalBankRecoveryService {

    private final ExternalBankGateway gateway;
    private final CircuitBreaker circuitBreaker;

    public ExternalBankRecoveryService(ExternalBankGateway gateway, @Qualifier("bankTransferCircuitBreaker") CircuitBreaker circuitBreaker){
        this.gateway = gateway;
        this.circuitBreaker = circuitBreaker;
    }

    //외부 HTTP 호출을 수행하므로 DB 트랜잭션을 걸지 않는다.
    public BankOutcome submit(BankTransferRequest request, String scenario){
        try {
            BankTransferResponse response = CircuitBreaker.decorateSupplier(circuitBreaker, () -> gateway.submit(request, scenario)).get();

            if(isCompleted(response, request)){
                return BankOutcome.COMPLETED;
            }

            //예상하지 못한 응답도 상태 조회로 확인
            return verifyStatus(request);
        } catch (HttpServerErrorException | ResourceAccessException e){

            //500 / Timeout: 실제 송금 성공 여부가 불명확
            return verifyStatus(request);
        }
    }

    private BankOutcome verifyStatus(BankTransferRequest request){
        try{
            Optional<BankTransferResponse> result = gateway.status(request.requestId());

            if(result.isPresent() && isCompleted(result.get(), request)){
                return BankOutcome.COMPLETED;
            }

            return BankOutcome.UNKNOWN;
        } catch (HttpServerErrorException | ResourceAccessException e){
            //상태 조회도 실패했으면 결과를 확정할 수 없음
            return BankOutcome.UNKNOWN;
        }
    }

    private boolean isCompleted(BankTransferResponse response, BankTransferRequest request){
        return response != null && "COMPLETED".equals(response.status()) && request.requestId().equals(response.requestId()) && request.amount() == response.amount();
    }
}
