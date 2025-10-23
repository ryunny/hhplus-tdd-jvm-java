# 포인트 관리 시스템

TDD(Test-Driven Development) 방식으로 구현한 사용자 포인트 충전/사용 시스템입니다.

## 목차
- [기능 목록](#기능-목록)
- [테스트 구조](#테스트-구조)
- [예외 처리](#예외-처리)
- [동시성 제어 방식](#동시성-제어-방식)
- [기술 스택](#기술-스택)
- [실행 방법](#실행-방법)
- [프로젝트 구조](#프로젝트-구조)

---

## 기능 목록

### 1. 포인트 조회
- 특정 유저의 포인트를 조회합니다.
- 유효하지 않은 사용자 ID(0 이하)에 대한 예외 처리가 포함되어 있습니다.

### 2. 포인트 충전
- 특정 유저의 포인트를 충전합니다.
- **최소 충전 금액**: 1원
- **최대 포인트 한도**: 1,000,000원
- 충전 후 합산 금액이 최대 한도를 초과할 수 없습니다.

### 3. 포인트 사용
- 특정 유저의 포인트를 사용합니다.
- **최소 사용 금액**: 1원
- 잔액이 부족한 경우 예외가 발생합니다.
- 사용 후 잔액은 0원 이상이어야 합니다.

### 4. 포인트 내역 조회
- 특정 유저의 포인트 충전/사용 내역을 조회합니다.
- 충전(CHARGE)과 사용(USE) 내역을 시간순으로 확인할 수 있습니다.

---

## 테스트 구조

### 단위 테스트 (PointServiceTest)
Mock 객체를 사용한 각 기능별 단위 테스트입니다.

**테스트 케이스 (총 20+개)**
- **포인트 조회**
  - 정상 조회
  - 음수 ID 예외
  - 0 ID 예외

- **포인트 충전**
  - 정상 충전
  - 합산 최대값 초과 예외
  - 음수 금액 예외
  - 음수/0 ID 예외

- **포인트 사용**
  - 정상 사용
  - 잔고 부족 예외
  - 음수 금액 예외
  - 0원 사용 예외
  - 음수/0 ID 예외

- **포인트 내역 조회**
  - 정상 조회
  - 음수/0 ID 예외

### 통합 테스트 (PointServiceIntegrationTest)
실제 구현체를 사용한 통합 테스트로, 여러 기능을 조합한 시나리오를 검증합니다.

**테스트 시나리오 (총 7개)**
1. 포인트 충전 후 조회
2. 포인트 충전 후 사용
3. 포인트 충전/사용 후 내역 조회
4. 여러 번 충전 후 포인트 누적 확인
5. 충전/사용 반복 후 최종 잔액 확인
6. 충전/사용 후 내역 순서 확인
7. 다른 유저 간 포인트 독립성 확인

### 동시성 테스트 (PointServiceConcurrencyTest)
멀티스레드 환경에서 동시성 제어가 올바르게 동작하는지 검증합니다.

**테스트 시나리오 (총 5개)**
1. **동시에 여러 번 충전**: 10개 스레드가 동시에 100원씩 충전 → 1000원 정확히 누적
2. **동시에 충전과 사용**: 충전 5회, 사용 3회 동시 실행 → 최종 금액 정확히 계산
3. **여러 사용자 동시 충전**: 5명의 사용자가 각각 3번씩 독립적으로 충전
4. **동시 사용 시 잔액 부족 처리**: 1000원으로 200원씩 10번 사용 시도 → 5번 성공, 5번 실패
5. **충전/사용 내역 정확성**: 동시 실행 후 내역 개수 및 타입 검증

**사용 기술**
- `ExecutorService`: 스레드 풀 관리
- `CountDownLatch`: 모든 스레드 작업 완료 대기
- `AtomicInteger`: 성공/실패 카운트

---

## 예외 처리

모든 예외는 `IllegalArgumentException`을 발생시킵니다.

### 유저 검증
| 상황 | 예외 메시지 |
|------|-------------|
| 음수 ID | "유효하지 않은 사용자 ID 입니다." |
| 0 ID | "유효하지 않은 사용자 ID 입니다." |

### 포인트 충전
| 상황 | 예외 메시지 |
|------|-------------|
| 음수 금액 | "포인트 충전 최소금액은 1 이상이여야 합니다." |
| 합산 금액 최대값 초과 | "포인트 최대금액1000000을 초과했습니다." |

### 포인트 사용
| 상황 | 예외 메시지 |
|------|-------------|
| 음수 금액 | "포인트 충전 최소금액은 1 이상이여야 합니다." |
| 0원 사용 | "포인트 충전 최소금액은 1 이상이여야 합니다." |
| 잔고 부족 | "포인트 사용 금액이 잔액보다 큽니다." |

---

## 동시성 제어 방식

### 구현 방식: 사용자별 ReentrantLock

#### 1. 핵심 구현 코드

```java
@Service
public class PointService {
    // 사용자별 Lock을 관리하는 Map
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    // 사용자별 Lock 획득
    private Lock getUserLock(long userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    public UserPoint chargePoint(long id, long amount) {
        validateUser(id);
        validateAmount_Min(amount);

        // 동시성 제어: 사용자별 Lock 획득
        Lock lock = getUserLock(id);
        lock.lock();
        try {
            // 포인트 충전 로직
            UserPoint currentPoint = userPointTable.selectById(id);
            long newPoint = amount + currentPoint.point();
            validateAmount_Max(newPoint);
            UserPoint userPoint = userPointTable.insertOrUpdate(id, newPoint);
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, userPoint.updateMillis());
            return userPoint;
        } finally {
            lock.unlock(); // 항상 Lock 해제
        }
    }

    public UserPoint usePoint(long id, long amount) {
        validateUser(id);
        validateAmount_Min(amount);

        // 동시성 제어: 사용자별 Lock 획득
        Lock lock = getUserLock(id);
        lock.lock();
        try {
            // 포인트 사용 로직
            long balance = userPointTable.selectById(id).point() - amount;
            validateBalance(balance);
            UserPoint userPoint = userPointTable.insertOrUpdate(id, balance);
            pointHistoryTable.insert(id, amount, TransactionType.USE, userPoint.updateMillis());
            return userPoint;
        } finally {
            lock.unlock(); // 항상 Lock 해제
        }
    }
}
```

#### 2. 동작 원리

**사용자별 독립적인 Lock 관리**
- `ConcurrentHashMap`에 사용자 ID를 키로 `ReentrantLock` 저장
- 같은 사용자의 요청: 순차적으로 처리 (동시성 문제 방지)
- 다른 사용자의 요청: 병렬로 처리 (성능 향상)

**Lock 획득 및 해제**
- `lock.lock()`: 작업 시작 전 Lock 획득, 다른 스레드는 대기
- `try-finally`: 예외 발생 시에도 반드시 Lock 해제
- `lock.unlock()`: 작업 완료 후 Lock 반환

#### 3. 동시성 문제 해결 과정

**문제 상황 (Lock 없을 때)**
```
시간    스레드A                    스레드B
t1      잔액 조회: 1000원
t2                                 잔액 조회: 1000원 (같은 값!)
t3      500원 사용 → 500원 저장
t4                                 300원 사용 → 700원 저장 (❌ 잘못됨)
결과    최종 잔액: 700원 (정답: 200원)
```

**해결 후 (Lock 사용)**
```
시간    스레드A                    스레드B
t1      Lock 획득 ✅
t2      잔액 조회: 1000원          Lock 대기 중... ⏳
t3      500원 사용 → 500원 저장
t4      Lock 해제 ✅
t5                                 Lock 획득 ✅
t6                                 잔액 조회: 500원 (올바른 값!)
t7                                 300원 사용 → 200원 저장
t8                                 Lock 해제 ✅
결과    최종 잔액: 200원 (✅ 정확함)
```

#### 4. 왜 이 방식을 선택했는가?

**선택 이유**
1. **데이터 정합성 보장**: 같은 사용자의 동시 요청을 완벽히 제어
2. **성능 효율**: 다른 사용자는 동시 처리 가능 (글로벌 Lock이 아님)
3. **구현 단순성**: 메모리 기반 환경에서 간단하게 구현 가능
4. **예외 안정성**: try-finally로 안전한 Lock 해제 보장
5. **환경 적합성**: DB 없는 메모리 기반 환경에 최적

**장점**
- Race Condition 완벽 방지
- 사용자별 독립적 처리로 성능 유지
- 코드 가독성 우수

**단점**
- 메모리 사용: 사용자 수만큼 Lock 객체 생성
- 단일 서버 제한: 분산 환경에서는 동작하지 않음

#### 5. 다른 방식과의 비교

| 동시성 제어 방식 | 장점 | 단점 | 현재 환경 적용 가능성 |
|-----------------|------|------|---------------------|
| **ReentrantLock** (✅ 선택) | • 사용자별 독립적 제어<br>• 구현 간단<br>• 명시적 Lock 관리 | • 메모리 사용<br>• 단일 서버만 가능 | ✅ 최적 |
| **synchronized** | • 구현 매우 간단<br>• JVM 레벨 지원 | • 전체 메서드 Lock<br>• 성능 저하 | ⚠️ 성능 이슈 |
| **낙관적 락** (Optimistic Lock) | • 충돌 적을 때 성능 우수<br>• 대기 시간 없음 | • 충돌 많을 때 재시도 오버헤드<br>• DB 필요 (Version 필드) | ❌ DB 없음 |
| **비관적 락** (Pessimistic Lock) | • 데이터 정합성 보장<br>• 충돌 많을 때 유리 | • DB의 SELECT FOR UPDATE 필요<br>• 대기 시간 증가 | ❌ DB 없음 |
| **분산 락** (Redis/Redisson) | • 다중 서버 환경 지원<br>• 확장성 우수 | • 외부 의존성 (Redis)<br>• 복잡도 증가<br>• 네트워크 비용 | ❌ Redis 없음 |

#### 6. 테스트로 검증한 내용

동시성 제어가 올바르게 동작하는지 다음 시나리오로 검증했습니다:

✅ **10개 스레드가 동시에 100원씩 충전** → 1000원 정확히 누적
✅ **충전 5회, 사용 3회 동시 실행** → 최종 금액 정확히 계산
✅ **여러 사용자 동시 처리** → 각 사용자별 독립적 동작
✅ **잔액 부족 시** → 정확히 5번 성공, 5번 실패
✅ **동시 실행 후 내역** → 개수 정확히 기록

#### 7. 향후 확장 방안

**분산 환경으로 확장 시 고려사항**

현재는 단일 서버 환경이지만, 향후 여러 서버로 확장할 경우:

1. **Redis 분산 락 (Redisson)**
   ```java
   RLock lock = redissonClient.getLock("user:lock:" + userId);
   lock.lock();
   try {
       // 포인트 처리
   } finally {
       lock.unlock();
   }
   ```

2. **DB 비관적 락**
   ```sql
   SELECT * FROM user_point WHERE id = ? FOR UPDATE;
   ```

3. **메시지 큐 (Kafka/RabbitMQ)**
   - 사용자별 파티션으로 순차 처리 보장

**현재 환경에서는 ReentrantLock이 최적의 선택입니다.**

---

## 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot
- **Test**: JUnit 5, AssertJ, Mockito
- **Concurrency**: java.util.concurrent (ReentrantLock, ConcurrentHashMap, ExecutorService, CountDownLatch)

---

## 실행 방법

### 전체 테스트 실행
```bash
./gradlew test
```

### 특정 테스트만 실행
```bash
# 단위 테스트
./gradlew test --tests PointServiceTest


# 통합 테스트
./gradlew test --tests PointServiceIntegrationTest

# 동시성 테스트
./gradlew test --tests PointServiceConcurrencyTest
```

### 빌드
```bash
./gradlew build
```

---

## 프로젝트 구조

```
src/
├── main/
│   └── java/io/hhplus/tdd/
│       ├── point/
│       │   ├── PointService.java          # 포인트 비즈니스 로직 (동시성 제어 포함)
│       │   ├── UserPoint.java             # 사용자 포인트 엔티티
│       │   ├── PointHistory.java          # 포인트 내역 엔티티
│       │   └── TransactionType.java       # 거래 타입 (CHARGE/USE)
│       └── database/
│           ├── UserPointTable.java        # 메모리 기반 사용자 포인트 저장소
│           └── PointHistoryTable.java     # 메모리 기반 포인트 내역 저장소
└── test/
    └── java/io/hhplus/tdd/
        ├── PointServiceTest.java              # 단위 테스트 (Mock 사용)
        ├── PointServiceIntegrationTest.java   # 통합 테스트 (실제 구현체 사용)
        └── PointServiceConcurrencyTest.java   # 동시성 테스트 (멀티스레드)
```

---

## 개발 과정 (TDD)

### STEP 01: 기본 기능 구현
- [x] 포인트 조회 기능 및 테스트
- [x] 포인트 충전 기능 및 테스트
- [x] 포인트 사용 기능 및 테스트
- [x] 포인트 내역 조회 기능 및 테스트

### STEP 02: 심화 기능
- [x] 예외 케이스 테스트 작성 (잔고 부족, 음수 금액 등)
- [x] 통합 테스트 작성 (전체 플로우 검증)
- [x] 동시성 테스트 작성 (Red - 테스트 실패 확인)
- [x] 동시성 제어 구현 (Green - ReentrantLock 적용)
- [x] README.md 동시성 제어 방식 문서화

### TDD 사이클

모든 기능은 다음 순서로 개발되었습니다:

1. **Red**: 실패하는 테스트 작성
2. **Green**: 테스트를 통과하는 최소한의 코드 작성
3. **Refactor**: 코드 개선 및 리팩토링

**동시성 제어 TDD 예시**
- Red: 동시성 제어 없이 테스트 실행 → 5개 테스트 실패
- Green: ReentrantLock 구현 → 모든 테스트 통과
- Refactor: 코드 정리 및 문서화

---

## AI 활용

### Claude Code 사용
프로젝트 전반에 걸쳐 Claude Code를 활용하여 TDD 기반 개발을 진행했습니다.

**주요 활용 사항:**
- TDD Red-Green-Refactor 사이클 진행
- 동시성 제어 구현 및 테스트 작성
- 코드 리뷰 및 개선 제안 반영
- 기술 문서(README.md) 작성

**효과적인 협업:**
- 명확한 요구사항 제시로 정확한 구현
- 피드백을 통한 코드 개선 반복
- AI 제안에 대한 검토 및 수정 요청

---

## 제약 사항

- 포인트 최대 한도: **1,000,000원**
- 포인트 최소 단위: **1원**
- 음수 포인트 불가
- 사용자 ID는 1 이상의 양수

---

## 버전 히스토리

- **v0.3**: 동시성 제어 구현 (ReentrantLock) 및 동시성 테스트 추가
- **v0.2**: 통합 테스트 추가, 예외 케이스 확장
- **v0.1**: 기본 포인트 기능 구현 (조회/충전/사용/내역)
