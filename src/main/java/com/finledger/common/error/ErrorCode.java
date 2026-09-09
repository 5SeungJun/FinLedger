package com.finledger.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-001", "요청 값이 올바르지 않습니다."),
    INVALID_MEMBER_NAME(HttpStatus.BAD_REQUEST, "MEMBER-001", "회원 이름은 비어 있을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-002", "회원을 찾을 수 없습니다."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "ACCOUNT-001", "계좌번호는 비어 있을 수 없습니다."),
    DUPLICATE_ACCOUNT_NUMBER(HttpStatus.CONFLICT, "ACCOUNT-002", "이미 사용 중인 계좌번호입니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT-003", "계좌를 찾을 수 없습니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "ACCOUNT-004", "금액은 0보다 커야 합니다."),
    INVALID_INITIAL_BALANCE(HttpStatus.BAD_REQUEST, "ACCOUNT-005", "초기 잔액은 0 이상이어야 합니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "ACCOUNT-006", "계좌 잔액이 부족합니다."),
    SAME_ACCOUNT_TRANSFER(HttpStatus.BAD_REQUEST, "TRANSFER-001", "같은 계좌로 송금할 수 없습니다."),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER-002", "송금 결과를 찾을 수 없습니다."),
    INVALID_TRANSFER_STATUS(HttpStatus.CONFLICT, "TRANSFER-003", "현재 상태에서는 송금을 완료할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
