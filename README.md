# FinLedger

금융 백엔드에서 발생하는 **트랜잭션, 동시성, 멱등성, 장애 복구, 대용량 처리** 문제를 직접 재현하고 해결하는 학습형 프로젝트입니다.

단순 CRUD 구현이 아니라, 실제 문제를 재현하고 원인을 분석한 뒤 해결 방법을 비교·적용하고 테스트로 검증하는 것을 목표로 합니다.

---

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Docker Compose
- JUnit 5
- AssertJ

향후 단계적으로 추가 예정:

- Kafka
- Redis
- Spring Batch
- Resilience4j
- Testcontainers
- k6
- Micrometer
- Prometheus
- Grafana

---

## 현재 구현

- Member
- Account
- 입금 / 출금
- 내부 계좌이체
- Transfer 상태 관리
- Transaction Rollback
- 동시 출금 테스트
- Pessimistic Lock

---

## 핵심 Invariant

```text
1. account.balance >= 0

2. 내부 송금 전후 시스템 총 자산은 동일해야 한다.

3. 성공한 송금은 정확한 금액만 이동해야 한다.

4. 출금과 입금은 함께 성공하거나 함께 실패해야 한다.

5. 실제 성공한 출금 횟수와 최종 잔액은 일치해야 한다.
```

---

# 1. Transaction Atomicity

## 문제 상황

내부 송금 과정에서 출금 처리 후 예외가 발생하면 출금만 DB에 반영되는 부분 실패가 발생할 수 있었다.

```text
A = 100,000원
B = 0원

30,000원 송금

A 출금 완료
↓
RuntimeException 발생
↓
B 입금 실패

결과
A = 70,000원
B = 0원
```

총 자산이 `100,000원 → 70,000원`으로 감소하면서 송금 불변식이 깨졌다.

## 원인 분석

송금 과정의 여러 DB 작업이 하나의 Transaction으로 묶여 있지 않아, 이미 완료된 DB 변경은 이후 예외가 발생해도 자동으로 취소되지 않았다.

## 해결 방법

`TransferService`의 송금 로직에 `@Transactional`을 적용했다.

```java
@Transactional
public Transfer transfer(
        Long fromAccountId,
        Long toAccountId,
        long amount
) {
    ...
}
```

## 검증

송금 처리 중 강제로 `RuntimeException`을 발생시킨 뒤 잔액을 다시 확인했다.

```text
장애 발생 후

A = 100,000원
B = 0원

총 자산 유지 ✅
부분 반영 방지 ✅
```

## 학습 내용

- Transaction
- ACID Atomicity
- Commit / Rollback
- `@Transactional`
- Persistence Context
- Dirty Checking

---

# 2. Concurrent Withdrawal

## 문제 상황

잔액이 10,000원인 동일 계좌에 8,000원 출금 요청 2개를 동시에 실행했다.

```text
초기 잔액: 10,000원
동시 요청: 8,000원 출금 × 2
```

Lock 적용 전:

```text
성공 요청: 2
최종 잔액: 2,000원
```

실제로는 16,000원의 출금이 성공했지만 잔액에는 한 번의 출금만 반영되는 `Lost Update`가 발생했다.

## 원인 분석

두 트랜잭션이 동시에 동일한 잔액을 조회했다.

```text
Transaction A
10,000 조회
→ 8,000 출금
→ 2,000 저장

Transaction B
10,000 조회
→ 8,000 출금
→ 2,000 저장
```

조회 → 계산 → 저장 사이에 다른 트랜잭션이 개입할 수 있었고, 한쪽 변경이 다른 변경을 덮어썼다.

`@Transactional`은 하나의 트랜잭션 내부 원자성을 보장하지만 서로 다른 트랜잭션 간 동시 접근까지 자동으로 제어하지는 않는다.

## 해결 방법 비교

| 방식 | 장점 | 단점 |
|---|---|---|
| 비관적 락 | 충돌을 사전에 차단하고 동작이 명확함 | 대기시간 증가, Deadlock 가능 |
| 낙관적 락 | Lock 점유가 없어 충돌이 적을 때 유리 | 충돌 시 예외 및 재시도 필요 |
| Atomic Update | 단순 조건부 갱신에 효율적 | 복잡한 송금 로직 확장에 제약 |

## 선택과 근거

현재 단계에서는 `PESSIMISTIC_WRITE`를 선택했다.

계좌 잔액은 정합성이 우선되는 데이터이며, 동일 계좌에 대한 동시 변경을 DB Row Lock으로 직렬화해 동작을 명확하게 확인하기 위해 비관적 락을 먼저 적용했다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select a
    from Account a
    where a.id = :id
""")
Optional<Account> findByIdForUpdate(Long id);
```

## 검증

같은 동시 출금 테스트를 다시 실행했다.

```text
초기 잔액: 10,000원
동시 요청: 8,000원 출금 × 2

성공: 1건
잔액 부족 실패: 1건
최종 잔액: 2,000원
```

실제 성공한 출금 횟수와 최종 잔액이 일치함을 확인했다.

## 한계

현재 검증은 단일 PostgreSQL 환경 기준이다.

추가로 검증해야 할 항목:

- Lock 경합 증가 시 처리량 저하
- Deadlock 가능성
- Optimistic Lock과의 성능 및 충돌 방식 비교
- Scale-out 환경에서의 동시성 제어

## 학습 내용

- Race Condition
- Lost Update
- Pessimistic Lock
- `SELECT FOR UPDATE`
- Transaction Isolation

---

## 테스트 범위

### Account

- 정상 입금
- 정상 출금
- 잔액 부족 출금 방지
- 0원 이하 금액 방지
- 동시 출금 문제 재현
- 비관적 락 적용 후 정합성 검증

### Transfer

- 정상 내부 송금
- 잔액 부족 송금 실패
- 송금 전후 총 자산 보존
- Transfer 상태 검증
- 송금 중 강제 장애
- 장애 발생 시 전체 Rollback

---

## 진행 상황

```text
✅ 프로젝트 초기 환경 구성
✅ Member / Account
✅ 입금 / 출금
✅ 내부 계좌이체
✅ 송금 불변식 검증

✅ Transaction Atomicity
✅ 송금 중 부분 반영 문제 재현
✅ @Transactional 적용
✅ Rollback 검증

✅ 동시 출금 문제 재현
✅ Race Condition / Lost Update
✅ Pessimistic Lock 적용
✅ 동시 출금 정합성 검증

▶ 다음
Optimistic Lock
Pessimistic vs Optimistic 비교
Deadlock 재현 및 해결

이후
Ledger
Idempotency
외부 시스템 장애
Connection Pool
Kafka
Transactional Outbox
Settlement Batch
DB Performance
Scale-out
Observability
```
