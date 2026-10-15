package com.finledger.externalbank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/mock-bank/transfers")
public class ExternalBankController {

    //외부 은행에서 처리한 요청을 모의 저장
    private final ConcurrentMap<String, BankTransferRequest> processed = new ConcurrentHashMap<>();

    public enum Scenario{
        SUCCESS,
        FAIL_BEFORE,
        FAIL_AFTER,
        TIMEOUT_AFTER
    }

    public record BankTransferRequest(String requestId, String fromAccount, String toAccount, long amount){}

    public record BankTransferResponse(String requestId, String status, long amount){}

    @PostMapping
    public ResponseEntity<?> transfer(@RequestBody BankTransferRequest request, @RequestHeader(value = "X-Simulation-Mode", defaultValue = "SUCCESS") Scenario scenario){
        if(request.requestId() == null
                || request.requestId().isBlank()
                || request.fromAccount == null
                || request.fromAccount.isBlank()
                || request.toAccount == null
                || request.toAccount.isBlank()
                || request.fromAccount().equals(request.toAccount())
                || request.amount() <= 0
        ) {
            return ResponseEntity.badRequest().build();
        }

        //이전에 완료된 요청인지 확인
        BankTransferRequest existing = processed.get(request.requestId());

        if(existing != null){
            return existing.equals(request) ? ResponseEntity.ok(toResponse(existing)) : conflict();
        }

        //송금 처리 이전 실패
        if(scenario == Scenario.FAIL_BEFORE){
            return serverError();
        }

        //같은 요청은 원자적으로 한 번만 처리
        existing = processed.putIfAbsent(request.requestId(), request);

        if(existing != null){
            return existing.equals(request) ? ResponseEntity.ok(toResponse(existing)) : conflict();
        }

        //송금 처리 이후 HTTP 500
        if(scenario == Scenario.FAIL_AFTER){
            return serverError();
        }

        //송금 처리 이후 응답 지연
        if(scenario == Scenario.TIMEOUT_AFTER){
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                return serverError();
            }
        }
        return ResponseEntity.ok(toResponse(request));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<BankTransferResponse> status(@PathVariable("requestId") String requestId){
        BankTransferRequest request = processed.get(requestId);

        if(request == null){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(request));
    }

    private BankTransferResponse toResponse(BankTransferRequest request){
        return new BankTransferResponse(request.requestId, "COMPLETED", request.amount());
    }

    private ResponseEntity<?> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "REQUEST_ID_CONFLICT"));
    }

    private ResponseEntity<?> serverError(){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "SIMULATED_FAILURE"));
    }
}
