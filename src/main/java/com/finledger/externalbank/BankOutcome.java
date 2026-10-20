package com.finledger.externalbank;

public enum BankOutcome {
    COMPLETED,  //외부 은행의 처리 완료 확인
    UNKNOWN,  //요청 이후 실제 처리 결과를 확정할 수 없음
    DEFERRED  //서킷 브레이커에 의해 이번 POST를 보내지 않고 보류
}
