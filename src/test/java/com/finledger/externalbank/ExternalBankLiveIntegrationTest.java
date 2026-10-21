
package com.finledger.externalbank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
@SpringBootTest
class ExternalBankLiveIntegrationTest {

    @Autowired
    ExternalBankRecoveryService service;
    @Autowired
    CircuitBreaker bankTransferCircuitBreaker;

    @BeforeEach
    void resetCircuitBreaker() {
        bankTransferCircuitBreaker.reset();
    }

    private BankTransferRequest createRequest() {
        return new BankTransferRequest(
                UUID.randomUUID().toString(),
                "ACCOUNT-A",
                "ACCOUNT-B",
                30_000L
        );
    }

    @Test
    @DisplayName("정상 응답을 받으면 송금 완료로 처리한다")
    void shouldCompleteOnSuccess() {

        BankOutcome outcome = service.submit(createRequest(), "SUCCESS");

        assertThat(outcome).isEqualTo(BankOutcome.COMPLETED);
    }

    @Test
    @DisplayName("외부 은행 처리 이후 500이 발생해도 완료 여부를 확인한다")
    void shouldRecoverAfterHttp500() {

        BankOutcome outcome = service.submit(createRequest(), "FAIL_AFTER");

        assertThat(outcome).isEqualTo(BankOutcome.COMPLETED);
    }

    @Test
    @DisplayName("응답 지연으로 타임아웃이 발생해도 상태 조회로 확인한다")
    void shouldRecoverAfterTimeout() {

        BankOutcome outcome = service.submit(createRequest(), "TIMEOUT_AFTER");

        assertThat(outcome).isEqualTo(BankOutcome.COMPLETED);
    }

    @Test
    @DisplayName("처리 전 장애 발생 시 완료로 단정하지 않는다")
    void shouldKeepUnknownAfterFailureBeforeProcessing() {

        BankOutcome outcome = service.submit(createRequest(), "FAIL_BEFORE");

        assertThat(outcome).isEqualTo(BankOutcome.UNKNOWN);
    }
}
