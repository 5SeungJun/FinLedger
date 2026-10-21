package com.finledger.externalbank;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ExternalBankRecoveryServiceTest {
    private ExternalBankGateway gateway;
    private CircuitBreaker circuitBreaker;
    private ExternalBankRecoveryService service;

    private final BankTransferRequest request = new BankTransferRequest("request-001", "Account-A", "Account-B", 30_000L);

    private final BankTransferResponse completed = new BankTransferResponse("request-001", "COMPLETED", 30_000L);

    @BeforeEach
    void setUp(){
        gateway = mock(ExternalBankGateway.class);
        circuitBreaker = CircuitBreaker.ofDefaults("testBank");

        service = new ExternalBankRecoveryService(gateway, circuitBreaker);
    }

    @Test
    @DisplayName("HTTP 500 이후 상태 조회로 송금 완료를 확인한다")
    void shouldConfirmTransferAfterHttp500(){
        when(gateway.submit(request, "FAIL_AFTER"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        when(gateway.status(request.requestId()))
                .thenReturn(Optional.of(completed));

        BankOutcome outcome = service.submit(request, "FAIL_AFTER");

        assertThat(outcome).isEqualTo(BankOutcome.COMPLETED);

        verify(gateway, times(1)).submit(request, "FAIL_AFTER");
        verify(gateway, times(1)).status(request.requestId());
    }

    @Test
    @DisplayName("타임아웃 이후 상태 조회로 송금 완료를 확인한다")
    void shouldConfirmTransferAfterTimeout(){
        when(gateway.submit(request, "TIMEOUT_AFTER"))
                .thenThrow(new ResourceAccessException("Read timed out"));

        when(gateway.status(request.requestId()))
                .thenReturn(Optional.of(completed));

        BankOutcome outcome = service.submit(request, "TIMEOUT_AFTER");

        assertThat(outcome).isEqualTo(BankOutcome.COMPLETED);

        verify(gateway, times(1)).submit(request, "TIMEOUT_AFTER");
    }

    @Test
    @DisplayName("상태를 확인할 수 없으면 결과 불명으로 유지한다")
    void shouldKeepUnknownWhenStatusIsMissing() {
        when(gateway.submit(request, "FAIL_BEFORE"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        when(gateway.status(request.requestId()))
                .thenReturn(Optional.empty());

        BankOutcome outcome = service.submit(request, "FAIL_BEFORE");

        assertThat(outcome).isEqualTo(BankOutcome.UNKNOWN);

        verify(gateway, times(1)).submit(request, "FAIL_BEFORE");
    }

    @Test
    @DisplayName("서킷 브레이커가 열리면 외부 요청을 전송하지 않는다")
    void shouldDeferWhenCircuitBreakerIsOpen() {

        circuitBreaker.transitionToOpenState();

        BankOutcome outcome =
                service.submit(request, "SUCCESS");

        assertThat(outcome).isEqualTo(BankOutcome.DEFERRED);

        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("요청 충돌은 재시도하거나 결과 불명으로 처리하지 않는다")
    void shouldRejectRequestConflict() {

        when(gateway.submit(request, "SUCCESS"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() ->
                service.submit(request, "SUCCESS")).isInstanceOf(HttpClientErrorException.Conflict.class);

        verify(gateway, never()).status(request.requestId());
    }
}

