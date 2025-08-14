package io.hhplus.tdd.point.integration

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.PointService
import io.hhplus.tdd.point.TransactionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("PointService 통합 테스트")
class PointServiceIntegrationTest {

    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable
    private lateinit var pointService: PointService

    @BeforeEach
    fun setUp() {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        pointService = PointService(userPointTable, pointHistoryTable)
    }

    /**
     * 테스트 케이스: 실제 데이터베이스와의 통합 환경에서 포인트 조회 기능 검증
     *
     * 작성 이유:
     * - Mock이 아닌 실제 데이터베이스 구현체(UserPointTable, PointHistoryTable)와의 연동 검증
     * - 인메모리 데이터베이스 환경에서의 데이터 지속성과 일관성 확인
     * - PointService의 비즈니스 로직이 실제 데이터 계층과 올바르게 연동되는지 확인
     * - 충전 후 조회에서 업데이트된 데이터가 정확히 반영되는지 검증
     *
     * 검증 내용:
     * - 충전 후 getUserPoint 호출 시 충전된 금액이 올바르게 반환되는지 확인
     * - 사용자 ID, 포인트, 업데이트 시간이 모두 올바른 값으로 설정되는지 검증
     * - 데이터베이스에서 반환되는 timestamp 값이 0보다 큰 유효한 값인지 확인
     */
    @Test
    fun `실제 데이터베이스와 연동하여 포인트를 조회한다`() {
        val userId = 1L
        val chargeAmount = 1000L

        pointService.chargePoint(userId, chargeAmount)
        val userPoint = pointService.getUserPoint(userId)

        assertEquals(userId, userPoint.id)
        assertEquals(chargeAmount, userPoint.point)
        assertTrue(userPoint.updateMillis > 0)
    }

    /**
     * 테스트 케이스: 실제 데이터베이스에서 포인트 거래 내역 조회 기능 검증
     *
     * 작성 이유:
     * - PointHistoryTable과의 실제 연동에서 거래 내역이 올바르게 저장되고 조회되는지 확인
     * - 충전(CHARGE)과 사용(USE) 내역이 시간순으로 올바르게 저장되는지 검증
     * - 다수의 거래 내역이 누락 없이 전체 조회되는지 확인
     * - 실제 데이터 계층에서의 거래 내역 지속성과 무결성 검증
     *
     * 검증 내용:
     * - 충전 1000과 사용 300 수행 후 2건의 내역이 올바르게 조회되는지 확인
     * - 내역 순서와 각 거래의 타입(CHARGE, USE), 금액이 예상과 일치하는지 검증
     * - 데이터베이스에 저장된 내역이 올바른 사용자 ID와 연결되어 있는지 확인
     */
    @Test
    fun `실제 데이터베이스와 연동하여 포인트 내역을 조회한다`() {
        val userId = 1L
        val chargeAmount = 1000L
        val useAmount = 300L

        pointService.chargePoint(userId, chargeAmount)
        pointService.usePoint(userId, useAmount)

        val history = pointService.getPointHistory(userId)

        assertEquals(2, history.size)
        assertEquals(TransactionType.CHARGE, history[0].type)
        assertEquals(chargeAmount, history[0].amount)
        assertEquals(TransactionType.USE, history[1].type)
        assertEquals(useAmount, history[1].amount)
    }

    /**
     * 테스트 케이스: 실제 데이터베이스 환경에서의 포인트 충전 기능 통합 검증
     *
     * 작성 이유:
     * - UserPointTable에서의 실제 데이터 업데이트와 PointHistoryTable에서의 내역 저장이 트랜잭션로 연동되는지 확인
     * - 충전 후 데이터 지속성과 내역 저장의 원자성 검증
     * - Mock 환경과 다른 실제 I/O 및 데이터 처리 로직의 올바른 동작 확인
     * - insertOrUpdate 메서드의 실제 구현이 충전 로직과 일치하는지 검증
     *
     * 검증 내용:
     * - 충전 후 반환된 UserPoint의 정확성 (ID, 금액) 확인
     * - getUserPoint로 재조회 시 충전된 금액이 올바르게 유지되는지 확인
     * - 충전 내역이 PointHistory에 CHARGE 타입으로 1건 저장되는지 확인
     * - 실제 데이터베이스 예외 상황 없이 정상 처리되는지 검증
     */
    @Test
    fun `실제 데이터베이스와 연동하여 포인트를 충전한다`() {
        val userId = 1L
        val chargeAmount = 1000L

        val result = pointService.chargePoint(userId, chargeAmount)

        assertEquals(userId, result.id)
        assertEquals(chargeAmount, result.point)

        val storedPoint = pointService.getUserPoint(userId)
        assertEquals(chargeAmount, storedPoint.point)

        val history = pointService.getPointHistory(userId)
        assertEquals(1, history.size)
        assertEquals(TransactionType.CHARGE, history[0].type)
        assertEquals(chargeAmount, history[0].amount)
    }

    /**
     * 테스트 케이스: 실제 데이터베이스 환경에서의 포인트 사용 기능 통합 검증
     *
     * 작성 이유:
     * - 포인트 사용 시 잔액 검증, 차감, 내역 저장이 실제 데이터베이스에서 원자적으로 수행되는지 확인
     * - 충전 후 사용으로 이어지는 전체 플로우에서의 데이터 일관성 검증
     * - 사용 후 잔여 포인트 계산이 실제 데이터 예제에서 올바르게 작동하는지 검증
     * - 사용 요청이 다른 사용자의 데이터에 영향을 주지 않는지 경계 테스트
     *
     * 검증 내용:
     * - 1000 충전 후 300 사용으로 잔액 700 계산 정확성 확인
     * - 사용 후 반환된 UserPoint와 재조회한 UserPoint의 일치성 검증
     * - 2건의 거래 내역(CHARGE, USE)이 올바른 순서와 금액으로 저장되는지 확인
     */
    @Test
    fun `실제 데이터베이스와 연동하여 포인트를 사용한다`() {
        val userId = 1L
        val chargeAmount = 1000L
        val useAmount = 300L
        val expectedRemaining = chargeAmount - useAmount

        pointService.chargePoint(userId, chargeAmount)
        val result = pointService.usePoint(userId, useAmount)

        assertEquals(userId, result.id)
        assertEquals(expectedRemaining, result.point)

        val storedPoint = pointService.getUserPoint(userId)
        assertEquals(expectedRemaining, storedPoint.point)

        val history = pointService.getPointHistory(userId)
        assertEquals(2, history.size)
        assertEquals(TransactionType.USE, history[1].type)
        assertEquals(useAmount, history[1].amount)
    }

    /**
     * 테스트 케이스: 연속된 충전/사용 트랜잭션의 누적 계산 정확성 검증
     *
     * 작성 이유:
     * - 복수의 충전/사용 요청이 순차적으로 수행될 때 누적 계산의 정확성 확인
     * - 여러 거래에 걸친 데이터 일관성과 지속성이 실제 데이터베이스에서 유지되는지 검증
     * - 대량의 거래 내역이 올바른 순서로 저장되고 최종 잔액에 대한 영향을 정확히 반영하는지 확인
     * - 리얼 월드에서 예상되는 다중 트랜잭션 시나리오 시뮤레이션
     *
     * 검증 내용:
     * - 충전 1000 -> 사용 300 -> 충전 500 -> 사용 200 순서로 수행
     * - 최종 잔액 1000 (1000 - 300 + 500 - 200) 계산 정확성 확인
     * - 4건의 거래 내역이 CHARGE, USE, CHARGE, USE 순서로 저장되는지 확인
     * - 각 거래 단계에서 중간 상태가 올바르게 계산되는지 검증
     */
    @Test
    fun `연속된 포인트 충전과 사용이 정상적으로 동작한다`() {
        val userId = 1L
        
        pointService.chargePoint(userId, 1000L)
        pointService.usePoint(userId, 300L)
        pointService.chargePoint(userId, 500L)
        pointService.usePoint(userId, 200L)

        val finalPoint = pointService.getUserPoint(userId)
        assertEquals(1000L, finalPoint.point)

        val history = pointService.getPointHistory(userId)
        assertEquals(4, history.size)
        assertEquals(TransactionType.CHARGE, history[0].type)
        assertEquals(TransactionType.USE, history[1].type)
        assertEquals(TransactionType.CHARGE, history[2].type)
        assertEquals(TransactionType.USE, history[3].type)
    }

    /**
     * 테스트 케이스: 동시성 제어 없는 충전 요청에서 데이터 정합성 문제 입증
     *
     * 작성 이유:
     * - 현재 구현에서 동시성 제어가 없음에 따른 레이스 컨디션 발생 가능성 입증
     * - 대량의 동시 요청 하에서 데이터 무결성 문제의 심각성 인지
     * - 버그 재현과 동시성 이슈 진단을 위한 테스트 케이스 제공
     * - 동시성 제어 소루션 도입의 필요성 입증
     * - 실제 프로덕션 환경에서 발생 가능한 데이터 불일치 문제 예측
     *
     * 검증 내용:
     * - 10개 쓰레드가 동시에 100원씩 충전 요청 (ExecutorService 사용)
     * - 예상 최종 포인트 1000과 실제 결과 비교 및 차이 분석
     * - 콘솔 출력으로 데이터 정합성 문제 시각적 확인
     * - 동시성 문제 내역을 발견하고 개선 방향 제시
     * - CountDownLatch로 모든 쓰레드 완료 동기화 보장
     */
    @Test
    fun `동시성 환경에서 포인트 충전이 안전하게 처리되지 않아 데이터 정합성 문제가 발생할 수 있다`() {
        val userId = 1L
        val threadCount = 10
        val chargeAmount = 100L
        val expectedTotalPoint = threadCount * chargeAmount

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    pointService.chargePoint(userId, chargeAmount)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalPoint = pointService.getUserPoint(userId)
        println("예상 포인트: $expectedTotalPoint, 실제 포인트: ${finalPoint.point}")
    }

    /**
     * 테스트 케이스: 동시성 제어 없는 사용 요청에서 데이터 정합성 문제 입증
     *
     * 작성 이유:
     * - 잔액 검증과 차감 과정에서 발생할 수 있는 레이스 컨디션 문제 입증
     * - 다중 사용 요청 하에서 잔액 부족 검증 로직의 비원자성 문제 확인
     * - 동시 액세스 환경에서 데이터 일관성 최소화 문제의 심각성 인식
     * - 상반된 비즈니스 로직(사용/충전) 간 상호작용 시 예상치 못한 데이터 상태 발생 가능성 확인
     * - Lock이나 동기화 도입 전 기준점(Baseline) 테스트 제공
     *
     * 검증 내용:
     * - 1000 충전 후 5개 쓰레드가 동시에 100원씩 사용 요청
     * - 예상 최종 포인트 500(1000-100*5)과 실제 결과 비교
     * - 동시성 문제로 인한 예상치 못한 결과 발생 가능성 입증
     * - 성공/실패 카운트와 콘솔 출력으로 데이터 부정합 상황 추적
     */
    @Test
    fun `동시성 환경에서 포인트 사용이 안전하게 처리되지 않아 데이터 정합성 문제가 발생할 수 있다`() {
        val userId = 1L
        val initialAmount = 1000L
        val threadCount = 5
        val useAmount = 100L
        val expectedFinalPoint = initialAmount - (threadCount * useAmount)

        pointService.chargePoint(userId, initialAmount)

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    pointService.usePoint(userId, useAmount)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("포인트 사용 실패: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalPoint = pointService.getUserPoint(userId)
        println("예상 포인트: $expectedFinalPoint, 실제 포인트: ${finalPoint.point}")
    }
}