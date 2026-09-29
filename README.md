# FinLedger

금융 백엔드에서 발생하는 **트랜잭션, 동시성, 멱등성, 장애 복구, 대용량 처리** 문제를 직접 재현하고 해결하는 프로젝트입니다.

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Docker Compose
- JUnit 5 / AssertJ

## 현재 구현

- Member
- Account
- 입금 / 출금
- 내부 계좌이체
- 송금 상태 관리
- Transaction Rollback 테스트

## 핵심 Invariant

```text
1. account.balance >= 0
2. 송금 전후 시스템 총 자산은 동일해야 한다.
3. 성공한 송금은 정확한 금액만 이동해야 한다.
4. 출금과 입금은 함께 성공하거나 함께 실패해야 한다.
```

## Transaction Atomicity

초기 송금 로직에서는 출금 처리 후 장애가 발생하면 출금만 DB에 반영되는 문제가 발생할 수 있었습니다.

```text
A = 100,000
B = 0

30,000원 송금

A 출금 완료
↓
RuntimeException
↓
B 입금 실패

결과
A = 70,000
B = 0
```

`@Transactional`을 적용해 송금 전체를 하나의 Transaction으로 처리했습니다.

```java
@Transactional
public Transfer transfer(...) {
    ...
}
```

이후 중간에 예외가 발생하면 전체 작업이 Rollback됩니다.

```text
장애 발생 후

A = 100,000
B = 0

총 자산 유지 ✅
부분 반영 방지 ✅
```

### 학습 내용

- Transaction
- ACID Atomicity
- Commit / Rollback
- `@Transactional`
- Persistence Context
- Dirty Checking

## 진행 상황

```text
✅ Member / Account
✅ 입금 / 출금
✅ 내부 송금
✅ 송금 불변식 검증
✅ Transaction 원자성 / Rollback

▶ 다음: 동시 출금 문제
   → Race Condition
   → Lost Update
   → Pessimistic Lock
   → Optimistic Lock
   → Deadlock
```
