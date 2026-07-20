# Stock Concurrency Lab

Java/Spring 환경에서 동시성 제어 방식을 비교 실험하는 프로젝트

## 실험 주제

동일한 재고 차감 시나리오에서 락 방식별 동작 차이 테스트

| 단계 | 방식 | 목적 |
|------|------|------|
| 1 | 락 없음 | 동시성 문제 발생 확인 |
| 2 | synchronized | Java 레벨 동기화 |
| 3 | 비관적 락 | DB 레벨 잠금 |
| 4 | 낙관적 락 | 충돌 감지 후 재시도 |

## 동시성 문제란?

여러 스레드가 **공유 자원에 동시에 접근**할 때 데이터 정합성이 깨지는 현상이다.  
재고 차감처럼 "읽기 → 수정 → 쓰기"가 원자적으로 처리되지 않으면 문제가 발생한다.

### Race Condition (경쟁 조건)

```
Thread A: 재고 조회 → 100
Thread B: 재고 조회 → 100   ← A가 저장하기 전에 읽음
Thread A: 100 - 1 = 99 저장
Thread B: 100 - 1 = 99 저장 ← A의 결과를 덮어씀
```

2번 차감이 일어났지만 재고는 99. **1번 차감이 유실**된다.  
이를 **Lost Update(갱신 손실)** 문제라고 한다.

### 왜 트랜잭션이 있어도 발생하는가?

MySQL의 기본 격리 수준인 `REPEATABLE READ`에서도 이 문제는 발생한다.  
트랜잭션은 데이터의 일관성을 보장하지만, 동시에 실행되는 트랜잭션 간의 **쓰기 충돌은 막아주지 않는다**.  
각 트랜잭션은 자신이 읽은 시점의 값을 기준으로 수정하기 때문에, 두 트랜잭션이 같은 값을 읽고 각자 저장하면 나중에 저장한 값이 이긴다.

## 해결 방법 비교

### 1단계: 락 없음 (현재)

```java
@Transactional
public void decrease(Long id, Long quantity) {
    var stock = stockRepository.findById(id).orElseThrow();
    stock.decrease(quantity);
}
```

- 동시 요청이 들어오면 Lost Update 발생
- 100개 스레드가 동시에 재고 1씩 차감해도 최종 재고가 0이 되지 않음

### 2단계: synchronized

```java
public synchronized void decrease(Long id, Long quantity) { ... }
```

- Java 레벨에서 메서드에 한 번에 하나의 스레드만 접근 허용
- **단점**: 서버가 여러 대인 경우(분산 환경) 효과 없음. 각 서버의 JVM이 독립적으로 동작하기 때문

### 3단계: 비관적 락 (Pessimistic Lock)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Stock s where s.id = :id")
Optional<Stock> findByIdWithPessimisticLock(@Param("id") Long id);
```

- DB에서 `SELECT ... FOR UPDATE`로 행(row)에 락을 걸어 다른 트랜잭션의 접근을 차단
- **장점**: 충돌이 많은 환경에서 안전하고 확실함
- **단점**: 락 대기로 인한 성능 저하, 데드락 가능성

### 4단계: 낙관적 락 (Optimistic Lock)

```java
@Version
private Long version;
```

- DB 락 없이 `@Version` 필드로 충돌을 감지
- 저장 시 버전이 다르면 `ObjectOptimisticLockingFailureException` 발생
- **장점**: 충돌이 적은 환경에서 성능 좋음
- **단점**: 충돌이 잦으면 재시도 로직이 복잡해지고 성능 저하

`OptimisticLockStockService`는 충돌 시 예외를 던지고 끝나므로, 트랜잭션 밖에 있는 `OptimisticLockStockFacade`가 예외를 잡아 짧게 대기(`Thread.sleep(50)`)한 뒤 새 트랜잭션으로 재시도한다.

```java
public void decrease(Long id, Long quantity) throws InterruptedException {
    while (true) {
        try {
            optimisticLockStockService.decrease(id, quantity);
            break;
        } catch (ObjectOptimisticLockingFailureException e) {
            Thread.sleep(50);
        }
    }
}
```

## 세 가지 락 방식 비교

| 방식 | 잠금 주체 | 충돌 처리 | 장점 | 단점 |
|------|-----------|-----------|------|------|
| synchronized | JVM (객체 모니터) | 한 번에 하나의 스레드만 진입 허용 | 구현이 간단하고 별도 DB 설정 불필요 | 분산 환경(서버 2대 이상)에서는 효과 없음 |
| 비관적 락 | DB (`SELECT ... FOR UPDATE`) | 락을 잡고 다른 트랜잭션을 대기시킴 | 여러 서버에서도 동작, 충돌이 잦아도 안전 | 락 대기로 인한 성능 저하, 데드락 가능성 |
| 낙관적 락 | 애플리케이션 (`@Version`) | 커밋 시점에 버전 비교, 충돌 시 예외 → 재시도 | DB 락이 없어 충돌이 적을 때 성능이 가장 좋음 | 충돌이 잦으면 재시도 비용 증가, 재시도 로직을 직접 구현해야 함 |

## 테스트 방식

`CountDownLatch`로 100개 스레드를 동시에 출발시켜 실제 운영 환경의 동시 요청을 시뮬레이션한다.

```java
ExecutorService executorService = Executors.newFixedThreadPool(32);
CountDownLatch latch = new CountDownLatch(100);

for (int i = 0; i < 100; i++) {
    executorService.submit(() -> {
        try {
            stockService.decrease(stockId, 1L);
        } finally {
            latch.countDown();
        }
    });
}

latch.await(); // 모든 스레드 완료 대기
```

- **락 없음**: `assertNotEquals(0, quantity)` → 통과 (문제 발생 확인)
- **락 적용 후**: `assertEquals(0, quantity)` → 통과 (정합성 보장 확인)

## 테스트 결과

초기 재고 100개, 100개 스레드(스레드 풀 크기 32)로 1개씩 동시 차감했을 때의 실측 결과다.

| 방식 | 차감 후 재고 | 결과 | 소요 시간 |
|------|--------------|------|-----------|
| 락 없음 | 90 (예시, 실행마다 달라짐) | ❌ Lost Update 발생 | 0.087s |
| synchronized | 0 | ✅ 정합성 보장 | 0.69s |
| 비관적 락 | 0 | ✅ 정합성 보장 | 0.392s |
| 낙관적 락 | 0 | ✅ 정합성 보장 (재시도 포함) | 1.585s |

- 락 없음: 100번 차감했지만 동시 접근으로 일부 갱신이 유실되어 재고가 0보다 크게 남는다. 유실되는 개수는 스레드 스케줄링에 따라 실행마다 달라진다.
- synchronized / 비관적 락 / 낙관적 락: 모두 재고가 정확히 0이 되어 정합성이 보장됨을 확인했다.
- 소요 시간은 로컬 1회 실행 기준이라 벤치마크로 보기는 어렵지만, 낙관적 락은 버전 충돌 시 `Thread.sleep(50)` 후 재시도하는 구조라 충돌이 잦은 이 테스트(스레드 100개가 같은 행 하나를 동시에 갱신)에서는 다른 방식보다 느리게 나타났다.

## 기술 스택

- Java 17
- Spring Boot 4.0.6
- MySQL
- JPA
- JUnit5

## 실행 방법

추후 작성 예정
