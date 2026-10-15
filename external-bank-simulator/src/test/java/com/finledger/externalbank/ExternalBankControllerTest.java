package com.finledger.externalbank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalBankControllerTest {
    private final ExternalBankController controller = new ExternalBankController();
    private final ExternalBankController.BankTransferRequest request = new ExternalBankController.BankTransferRequest("request-001", "Account-A", "Account-B", 30_000L);

    @Test
    @DisplayName("처리 이전 장애는 송금 기록을 남기지 않는다")
    void failBeforeTest(){
         controller.transfer(request, ExternalBankController.Scenario.FAIL_BEFORE);

         assertThat(controller.status("request-001").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("처리 이후 장애는 응답이 실패해도 송금이 완료되어 있다")
    void failAfterTest(){
        ResponseEntity<?> response = controller.transfer(request, ExternalBankController.Scenario.FAIL_AFTER);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(controller.status("request-001").getBody().status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("동일한 요청은 중복 처리하지 않는다")
    void sameRequestTest(){
        controller.transfer(request, ExternalBankController.Scenario.SUCCESS);

        ResponseEntity<?> response = controller.transfer(request, ExternalBankController.Scenario.SUCCESS);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void sameRequestIdTest(){
        controller.transfer(request, ExternalBankController.Scenario.SUCCESS);

        var different = new ExternalBankController.BankTransferRequest(
                "request-001",
                "Account-A",
                "Account-B",
                50_000L
        );

        ResponseEntity<?> response = controller.transfer(different, ExternalBankController.Scenario.SUCCESS);
        assertThat(response.getStatusCode().value()).isEqualTo(409);

    }
}
