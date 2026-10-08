package com.finledger.idempotency;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException() {
        super("동일한 멱등성 키가 다른 요청에 사용되었습니다.");
    }
}
