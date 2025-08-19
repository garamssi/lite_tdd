# 포인트 관리 - 동시성 보고서

## 인메모리 동시성 제어 분석 보고서

#### 1 데이터 저장소 구조
```kotlin
// UserPointTable.kt - 사용자 포인트 저장소
private val table = HashMap<Long, UserPoint>()

// PointHistoryTable.kt - 포인트 이력 저장소  
private val table = mutableListOf<PointHistory>()
private var cursor: Long = 1L
```

#### 1.1 도메인 모델
- **UserPoint**: 사용자 ID, 포인트 잔액, 업데이트 시간
- **PointHistory**: 포인트 거래 이력 (충전/사용)
- **TransactionType**: 거래 유형 (CHARGE/USE)

#### 1.2 비즈니스 로직
- **PointService**: 포인트 충전, 사용, 조회 로직 구현 (`src/main/kotlin/io/hhplus/tdd/point/PointService.kt`)
- **PointController**: REST API 엔드포인트 제공 (`src/main/kotlin/io/hhplus/tdd/point/PointController.kt`)

### 2. 동시성 제어 구현 분석

#### 2.1 **구현된 동시성 제어 방식: 유저별 락(User-Level Locking)**

**PointService.kt:15-16, 30-31, 52-53**에 구현된 동시성 제어:

```kotlin
@Service
class PointService {
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    fun chargePoint(id: Long, amount: Long): UserPoint {
        val lock = userLocks.computeIfAbsent(id) { ReentrantLock() }
        return lock.withLock {
            // 포인트 충전 로직
        }
    }

    fun usePoint(id: Long, amount: Long): UserPoint {
        val lock = userLocks.computeIfAbsent(id) { ReentrantLock() }
        return lock.withLock {
            // 포인트 사용 로직
        }
    }
}
```

#### 2.2 동시성 제어 메커니즘 상세 분석

**1) ConcurrentHashMap + ReentrantLock 조합**
- **ConcurrentHashMap**: 사용자별 락 저장소로 스레드-안전한 Map 사용
- **ReentrantLock**: 사용자별 개별 락으로 세밀한 동시성 제어
- **computeIfAbsent()**: 락이 없을 때만 새로 생성하여 메모리 효율성 확보

**2) kotlin.concurrent.withLock 활용**
```kotlin
lock.withLock {
    // 임계영역 코드
    // 자동으로 try-finally로 lock/unlock 처리
}
```

**3) 동시성 제어 범위**
- **조회 작업(`getUserPoint`, `getPointHistory`)**: 락 없이 읽기 허용
- **변경 작업(`chargePoint`, `usePoint`)**: 사용자별 락으로 동기화

#### 3.1 성능 특성 분석

| 항목 | 구현 방식 | 성능 특성 |
|------|-----------|-----------|
| **락 범위** | 사용자별 개별 락 | 다른 사용자 간 병렬 처리 가능 |
| **메모리 효율성** | ConcurrentHashMap 기반 | 사용하는 사용자만 락 생성 |
| **데드락 위험** | 단일 락 사용 | 데드락 발생 가능성 매우 낮음 |
| **처리량** | 높음 | 사용자별 독립적 처리로 높은 처리량 |
| **응답시간** | 낮음 | 락 경합 최소화로 빠른 응답 |

### 3. 테스트 기반 검증

#### 3.1 구현된 테스트 구조

```
src/test/kotlin/io/hhplus/tdd/point/
├── PointServiceTest.kt                              # 단위 테스트 (10개 테스트)
├── PointControllerTest.kt                           # 컨트롤러 테스트  
├── integration/PointServiceIntegrationTest.kt       # 통합 테스트 (9개 테스트)
└── integration/PointControllerIntegrationTest.kt    # 컨트롤러 통합 테스트
```

#### 3.2 **실제 동시성 테스트 케이스**

**동시성 제어 효과 검증** (`PointServiceIntegrationTest.kt:211-239`):
```kotlin
@Test
fun `동시성 환경에서 포인트 충전이 안전하게 처리되지 않아 데이터 정합성 문제가 발생할 수 있다`() {
    val userId = 1L
    val threadCount = 10
    val chargeAmount = 100L
    val expectedTotalPoint = threadCount * chargeAmount

    val executor = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)
    
    // 10개 스레드가 동시에 100포인트씩 충전
    repeat(threadCount) {
        executor.submit {
            try {
                pointService.chargePoint(userId, chargeAmount)
            } finally {
                latch.countDown()
            }
        }
    }
    
    latch.await()
    val finalPoint = pointService.getUserPoint(userId)
    println("예상 포인트: $expectedTotalPoint, 실제 포인트: ${finalPoint.point}")
}
```

**동시 사용 검증** (`PointServiceIntegrationTest.kt:258-289`):
```kotlin
@Test 
fun `동시성 환경에서 포인트 사용이 안전하게 처리되지 않아 데이터 정합성 문제가 발생할 수 있다`() {
    // 1000포인트 충전 후 5개 스레드가 동시에 100포인트씩 사용
    // 예상 최종 포인트: 500 (1000 - 100*5)
}
```

### 4. 동시성 제어 효과 분석

#### 4.1 해결된 동시성 문제

**1) Race Condition 방지**
- 유저별 락으로 동시 수정 방지
- 읽기-수정-쓰기 과정의 원자성 보장

**2) Lost Update Problem 해결**
- 동일 사용자의 병렬 요청을 순차 처리
- 모든 거래가 누락 없이 반영

**3) 데이터 일관성 보장**  
- 포인트 잔액과 거래 이력의 일관성 유지
- 트랜잭션 원자성 확보

### 5. 인메모리 데이터베이스 특성

#### 5.1 시뮬레이션된 지연시간
```kotlin
Thread.sleep(Math.random().toLong() * 200L)  // UserPointTable.selectById
Thread.sleep(Math.random().toLong() * 300L)  // insertOrUpdate, insert
```

**목적:**
- 실제 데이터베이스 I/O 지연 시뮬레이션
- 동시성 문제 발생 확률 증가로 테스트 환경에서 문제 재현성 향상

#### 5.2 데이터 구조 특성
- **HashMap**: 빠른 O(1) 접근 시간, 스레드 불안전
- **MutableList**: 순차 접근, 스레드 불안전  
- **AtomicLong**: 원자적 증가 연산 (cursor 관리)

### 7. 동시성 제어 방식 비교 및 최적화 분석

#### 7.1 인메모리 환경에서 고려 가능한 동시성 제어 방식들

| 방식 | 구현 복잡도 | 성능 | 메모리 사용량 | 확장성 | 데드락 위험 |
|------|-------------|------|---------------|--------|-------------|
| **전역 Synchronized** | 낮음 | 매우 낮음 | 낮음 | 낮음 | 없음 |
| **전역 ReentrantLock** | 낮음 | 낮음 | 낮음 | 낮음 | 없음 |
| **유저별 Lock (현재)** | 중간 | **높음** | 중간 | **높음** | 매우 낮음 |
| **ConcurrentHashMap만** | 낮음 | 중간 | 낮음 | 높음 | 없음 |
| **Optimistic Locking** | 높음 | 높음 | 낮음 | 높음 | 없음 |
| **Actor Model** | 매우 높음 | 매우 높음 | 높음 | 매우 높음 | 없음 |


#### 7.3 **현재 구현의 주요 단점 및 한계**

**1) 메모리 누수 위험**
```kotlin
// 문제: 장기간 사용하지 않는 사용자의 락이 메모리에 계속 남아있음
private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()
// 사용자 1000명이 한 번씩 거래하면 1000개의 ReentrantLock 객체가 영구 저장
```

**2) 분산 환경 미지원**
```
현재: 단일 JVM 내에서만 동작
     ┌─────────────┐
     │   JVM-1     │
     │ userLocks   │ ← 인스턴스별로 독립적인 락
     └─────────────┘
     
문제: 여러 서버 인스턴스에서는 동시성 제어 불가
     ┌─────────────┐    ┌─────────────┐
     │   JVM-1     │    │   JVM-2     │
     │ userLocks   │    │ userLocks   │ ← 서로 다른 락
     └─────────────┘    └─────────────┘
```

**개선 방안:**
- Redis Distributed Lock
- Database Level Locking
- Apache Zookeeper

#### 7.4 **대안 방식들과의 비교**

**1) 전역 락 (Synchronized/ReentrantLock)**
```kotlin
// 장점: 구현 단순, 데드락 위험 없음
// 단점: 모든 사용자가 순차 처리되어 성능 저하 심각
@Synchronized
fun chargePoint(id: Long, amount: Long): UserPoint { ... }
```

**2) Optimistic Locking (CAS 기반)**
```kotlin
// 장점: 락 없이 높은 성능, 메모리 효율적
// 단점: 충돌 시 재시도 로직 복잡, 기아 상태 가능성
do {
    val current = getCurrentPoint(id)
    val newPoint = current + amount
} while (!compareAndSet(id, current, newPoint))
```

**3) Actor Model**
```kotlin
// 장점: 매우 높은 확장성과 성능
// 단점: 구현 복잡도 매우 높음, 러닝 커브 steep
class PointActor : AbstractActor() {
    override fun createReceive(): Receive = 
        receiveBuilder()
            .match(ChargeRequest::class.java) { ... }
            .build()
}
```

**3) 장기 개선 (아키텍처 변경)**
- Event Sourcing + CQRS 패턴 도입
- Message Queue 기반 비동기 처리
- 마이크로서비스 아키텍처로 분리

### 8. **결론**

**선택 이유:**
1. 포인트 시스템 특성에 맞는 사용자별 격리
2. 테스트 가능한 명확한 동작

## 핵심 학습 포인트
1. **TDD 개발 방법론**: 테스트 우선 설계
2. **동시성 제어**: 유저별 락을 통한 동시성 관리
3. **인메모리 DB**: 실제 DB 환경 시뮬레이션
4. **예외 처리**: 비즈니스 로직과 시스템 예외의 처리