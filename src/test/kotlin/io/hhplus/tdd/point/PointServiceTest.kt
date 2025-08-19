package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
@DisplayName("PointService 단위 테스트")
class PointServiceTest {

    @Mock
    private lateinit var userPointTable: UserPointTable

    @Mock
    private lateinit var pointHistoryTable: PointHistoryTable

    private lateinit var pointService: PointService

    @BeforeEach
    fun setUp() {
        pointService = PointService(userPointTable, pointHistoryTable)
    }

    /**
     * 테스트 케이스: 존재하는 사용자의 포인트 조회 성공
     *
     * 작성 이유:
     * - PointService의 핵심 기능인 사용자 포인트 조회 기능을 검증
     * - UserPointTable의 selectById 메서드가 정상적으로 호출되는지 확인
     * - 반환된 데이터의 정확성(id, point, updateMillis)을 검증
     * - Mock 객체를 사용하여 의존성을 격리하고 단위 테스트의 순수성 보장
     *
     * 검증 내용:
     * - 사용자 ID, 포인트, 업데이트 시간이 예상값과 일치하는지 확인
     * - UserPointTable.selectById가 정확히 한 번 호출되었는지 Mock 검증
     */
    @Test
    fun `존재하는 사용자의 포인트를 정상적으로 조회한다`() {
        // Given: 사용자 포인트 데이터 준비
        val userId = 1L
        val expectedPoint = 1000L
        val timestamp = System.currentTimeMillis()
        val expectedUserPoint = UserPoint(userId, expectedPoint, timestamp)

        // Stub: userPointTable이 해당 사용자 정보를 반환하도록 설정
        `when`(userPointTable.selectById(userId)).thenReturn(expectedUserPoint)

        // When: 포인트 조회
        val actualUserPoint = pointService.getUserPoint(userId)

        // Then: 결과 검증
        assertAll(
            "사용자 포인트 정보 검증",
            { assertNotNull(actualUserPoint) },
            { assertEquals(userId, actualUserPoint.id) },
            { assertEquals(expectedPoint, actualUserPoint.point) },
            { assertEquals(timestamp, actualUserPoint.updateMillis) }
        )

        // Mock 검증: userPointTable.selectById가 정확히 한 번 호출되었는지 확인
        verify(userPointTable).selectById(userId)
    }

    /**
     * 테스트 케이스: 존재하지 않는 사용자의 포인트 조회 시 기본값 반환
     *
     * 작성 이유:
     * - 존재하지 않는 사용자에 대한 예외적 상황 처리를 검증
     * - 시스템의 견고성을 보장하기 위해 null 반환이 아닌 기본값 반환 정책 확인
     * - 데이터베이스 레이어(UserPointTable)의 기본 동작 정의 검증
     *
     * 검증 내용:
     * - 존재하지 않는 사용자 ID에 대해 기본값(포인트 0)이 반환되는지 확인
     * - 예외 발생 없이 안전하게 처리되는지 확인
     */
    @Test
    fun `존재하지 않는 사용자의 경우 기본값을 가진 포인트 정보를 반환한다`() {
        // Given: 존재하지 않는 사용자 ID
        val nonExistentUserId = 999L
        val defaultUserPoint = UserPoint(nonExistentUserId, 0L, System.currentTimeMillis())

        // Stub: userPointTable이 기본값을 반환하도록 설정
        `when`(userPointTable.selectById(nonExistentUserId)).thenReturn(defaultUserPoint)

        // When: 존재하지 않는 사용자의 포인트 조회
        val actualUserPoint = pointService.getUserPoint(nonExistentUserId)

        // Then: 기본값 반환 검증
        assertAll(
            "기본 포인트 정보 검증",
            { assertEquals(nonExistentUserId, actualUserPoint.id) },
            { assertEquals(0L, actualUserPoint.point) }
        )

        verify(userPointTable).selectById(nonExistentUserId)
    }

    /**
     * 테스트 케이스: 사용자의 포인트 거래 내역 조회 성공
     *
     * 작성 이유:
     * - 포인트 거래 내역 조회 기능의 정상 동작 검증
     * - 충전(CHARGE)과 사용(USE) 내역이 모두 정확하게 반환되는지 확인
     * - PointHistoryTable과의 연동이 정상적으로 작동하는지 검증
     * - 거래 타입별 금액 정보의 정확성 확인
     *
     * 검증 내용:
     * - 반환된 내역 리스트의 크기와 각 거래의 타입, 금액이 예상값과 일치하는지 확인
     * - PointHistoryTable.selectAllByUserId가 정확히 한 번 호출되었는지 Mock 검증
     */
    @Test
    fun `사용자의 포인트 내역을 정상적으로 조회한다`() {
        // Given: 사용자와 거래 내역 준비
        val userId = 1L
        val chargeHistory = PointHistory(1L, userId, TransactionType.CHARGE, 1000L, System.currentTimeMillis())
        val useHistory = PointHistory(2L, userId, TransactionType.USE, 500L, System.currentTimeMillis())
        val expectedHistoryList = listOf(chargeHistory, useHistory)

        // Stub: pointHistoryTable이 거래 내역을 반환하도록 설정
        `when`(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistoryList)

        // When: 포인트 내역 조회
        val actualHistory = pointService.getPointHistory(userId)

        // Then: 내역 검증
        assertAll(
            "포인트 내역 검증",
            { assertNotNull(actualHistory) },
            { assertEquals(2, actualHistory.size) },
            { assertEquals(TransactionType.CHARGE, actualHistory[0].type) },
            { assertEquals(1000L, actualHistory[0].amount) },
            { assertEquals(TransactionType.USE, actualHistory[1].type) },
            { assertEquals(500L, actualHistory[1].amount) }
        )

        verify(pointHistoryTable).selectAllByUserId(userId)
    }

    /**
     * 테스트 케이스: 거래 내역이 없는 사용자의 빈 리스트 반환
     *
     * 작성 이유:
     * - 거래 내역이 없는 신규 사용자나 활동하지 않은 사용자에 대한 처리 검증
     * - null 반환이 아닌 빈 리스트 반환으로 NPE 방지 정책 확인
     * - 클라이언트 코드에서 안전하게 처리할 수 있도록 일관된 반환 타입 보장
     *
     * 검증 내용:
     * - 빈 리스트가 반환되는지 확인 (null이 아님)
     * - 리스트 크기가 0인지 확인
     */
    @Test
    fun `거래 내역이 없는 사용자의 경우 빈 리스트를 반환한다`() {
        // Given: 거래 내역이 없는 사용자
        val userId = 1L
        val emptyHistoryList = emptyList<PointHistory>()

        // Stub: pointHistoryTable이 빈 리스트를 반환하도록 설정
        `when`(pointHistoryTable.selectAllByUserId(userId)).thenReturn(emptyHistoryList)

        // When: 포인트 내역 조회
        val actualHistory = pointService.getPointHistory(userId)

        // Then: 빈 리스트 반환 검증
        assertAll(
            "빈 내역 검증",
            { assertNotNull(actualHistory) },
            { assertTrue(actualHistory.isEmpty()) }
        )

        verify(pointHistoryTable).selectAllByUserId(userId)
    }

    /**
     * 테스트 케이스: 유효한 금액으로 포인트 충전 성공
     *
     * 작성 이유:
     * - PointService의 핵심 비즈니스 로직인 포인트 충전 기능 검증
     * - 기존 포인트에 충전 금액이 정확하게 합산되는지 확인
     * - 데이터베이스 업데이트와 히스토리 저장이 올바른 순서로 실행되는지 검증
     * - InOrder를 사용하여 메서드 호출 순서의 정확성 확인 (조회 -> 업데이트 -> 히스토리 저장)
     *
     * 검증 내용:
     * - 충전 후 포인트 합계가 정확한지 확인
     * - 각 의존성 메서드들이 올바른 파라미터로 호출되었는지 검증
     * - 메서드 호출 순서가 비즈니스 로직에 맞는지 확인
     */
    @Test
    fun `유효한 금액으로 포인트를 정상적으로 충전한다`() {
        // Given: 초기 포인트와 충전 금액 설정
        val userId = 1L
        val initialPoint = 500L
        val chargeAmount = 1000L
        val expectedNewPoint = initialPoint + chargeAmount
        val timestamp = System.currentTimeMillis()

        val currentUserPoint = UserPoint(userId, initialPoint, timestamp - 1000)
        val updatedUserPoint = UserPoint(userId, expectedNewPoint, timestamp)
        val pointHistory = PointHistory(1L, userId, TransactionType.CHARGE, chargeAmount, timestamp)

        // Stub: 의존성 동작 설정
        `when`(userPointTable.selectById(userId)).thenReturn(currentUserPoint)
        `when`(userPointTable.insertOrUpdate(userId, expectedNewPoint)).thenReturn(updatedUserPoint)
        `when`(pointHistoryTable.insert(userId, chargeAmount, TransactionType.CHARGE, updatedUserPoint.updateMillis))
            .thenReturn(pointHistory)

        // When: 포인트 충전
        val actualUserPoint = pointService.chargePoint(userId, chargeAmount)

        // Then: 충전 결과 검증
        assertAll(
            "포인트 충전 결과 검증",
            { assertEquals(userId, actualUserPoint.id) },
            { assertEquals(expectedNewPoint, actualUserPoint.point) },
            { assertEquals(timestamp, actualUserPoint.updateMillis) }
        )

        // Mock 검증: 메서드들이 올바른 순서와 파라미터로 호출되었는지 확인
        val inOrderVerifier: InOrder = inOrder(userPointTable, pointHistoryTable)
        inOrderVerifier.verify(userPointTable).selectById(userId)
        inOrderVerifier.verify(userPointTable).insertOrUpdate(userId, expectedNewPoint)
        inOrderVerifier.verify(pointHistoryTable).insert(userId, chargeAmount, TransactionType.CHARGE, timestamp)
    }

    /**
     * 테스트 케이스: 유효하지 않은 충전 금액에 대한 예외 처리
     *
     * 작성 이유:
     * - 비즈니스 규칙 위반에 대한 예외 처리 검증 (0원 이하 충전 금지)
     * - 잘못된 입력값에 대한 방어적 프로그래밍 구현 확인
     * - 시스템의 데이터 무결성 보장을 위한 유효성 검증 로직 테스트
     * - 예외 발생 시 데이터베이스 작업이 수행되지 않는지 확인 (원자성 보장)
     *
     * 검증 내용:
     * - 0원과 음수 금액에 대해 IllegalArgumentException 발생 확인
     * - 예외 메시지의 정확성 검증
     * - 예외 발생 시 다른 메서드들이 호출되지 않았는지 Mock 검증
     */
    @Test
    fun `0 이하의 금액으로 충전 시도하면 예외가 발생한다`() {
        // Given: 유효하지 않은 충전 금액들
        val userId = 1L
        val zeroAmount = 0L
        val negativeAmount = -100L

        // When & Then: 0원 충전 시도
        val zeroException = assertThrows<IllegalArgumentException> {
            pointService.chargePoint(userId, zeroAmount)
        }
        assertEquals("충전 금액은 0보다 커야 합니다", zeroException.message)

        // When & Then: 음수 금액 충전 시도
        val negativeException = assertThrows<IllegalArgumentException> {
            pointService.chargePoint(userId, negativeAmount)
        }
        assertEquals("충전 금액은 0보다 커야 합니다", negativeException.message)

        // Mock 검증: 유효성 검사에서 실패하므로 다른 메서드들은 호출되지 않아야 함
        verifyNoMoreInteractions(userPointTable)
        verifyNoMoreInteractions(pointHistoryTable)
    }

    /**
     * 테스트 케이스: 충분한 잔액이 있는 경우 포인트 사용 성공
     *
     * 작성 이유:
     * - 포인트 사용이라는 핵심 비즈니스 로직의 정상 동작 검증
     * - 기존 포인트에서 사용 금액이 정확하게 차감되는지 확인
     * - 데이터베이스 업데이트와 히스토리 저장의 트랜잭션 순서 검증
     * - InOrder를 사용하여 잔액 확인 -> 업데이트 -> 히스토리 저장 순서 확인
     *
     * 검증 내용:
     * - 사용 후 잔여 포인트가 정확하게 계산되는지 확인
     * - 각 의존성 메서드들이 올바른 파라미터로 호출되었는지 검증
     * - 메서드 호출 순서가 비즈니스 로직에 맞는지 확인
     */
    @Test
    fun `충분한 잔액이 있을 때 포인트를 정상적으로 사용한다`() {
        // Given: 충분한 잔액을 가진 사용자
        val userId = 1L
        val initialPoint = 1000L
        val useAmount = 300L
        val expectedNewPoint = initialPoint - useAmount
        val timestamp = System.currentTimeMillis()

        val currentUserPoint = UserPoint(userId, initialPoint, timestamp - 1000)
        val updatedUserPoint = UserPoint(userId, expectedNewPoint, timestamp)
        val pointHistory = PointHistory(1L, userId, TransactionType.USE, useAmount, timestamp)

        // Stub: 의존성 동작 설정
        `when`(userPointTable.selectById(userId)).thenReturn(currentUserPoint)
        `when`(userPointTable.insertOrUpdate(userId, expectedNewPoint)).thenReturn(updatedUserPoint)
        `when`(pointHistoryTable.insert(userId, useAmount, TransactionType.USE, updatedUserPoint.updateMillis))
            .thenReturn(pointHistory)

        // When: 포인트 사용
        val actualUserPoint = pointService.usePoint(userId, useAmount)

        // Then: 사용 결과 검증
        assertAll(
            "포인트 사용 결과 검증",
            { assertEquals(userId, actualUserPoint.id) },
            { assertEquals(expectedNewPoint, actualUserPoint.point) },
            { assertEquals(timestamp, actualUserPoint.updateMillis) }
        )

        // Mock 검증
        val inOrderVerifier: InOrder = inOrder(userPointTable, pointHistoryTable)
        inOrderVerifier.verify(userPointTable).selectById(userId)
        inOrderVerifier.verify(userPointTable).insertOrUpdate(userId, expectedNewPoint)
        inOrderVerifier.verify(pointHistoryTable).insert(userId, useAmount, TransactionType.USE, timestamp)
    }

    /**
     * 테스트 케이스: 잔액 부족 시 포인트 사용 실패
     *
     * 작성 이유:
     * - 비즈니스 규칙 위반에 대한 예외 처리 검증 (잔액 부족 시 사용 금지)
     * - 금융 시스템의 핵심 규칙인 잔액 검증 로직 테스트
     * - 시스템의 데이터 무결성과 일관성 보장 확인
     * - 실패 시 데이터베이스 업데이트가 수행되지 않는지 확인 (원자성 보장)
     *
     * 검증 내용:
     * - 잔액 부족 시 IllegalArgumentException 발생 확인
     * - 예외 메시지의 정확성 검증
     * - 잔액 확인 후 실패 시 업데이트 관련 메서드들이 호출되지 않았는지 Mock 검증
     */
    @Test
    fun `잔액이 부족한 경우 예외가 발생한다`() {
        // Given: 부족한 잔액을 가진 사용자
        val userId = 1L
        val currentPoint = 1000L
        val useAmount = 1500L
        val timestamp = System.currentTimeMillis()

        val currentUserPoint = UserPoint(userId, currentPoint, timestamp)

        // Stub: 현재 포인트 조회만 설정
        `when`(userPointTable.selectById(userId)).thenReturn(currentUserPoint)

        // When & Then: 잔액 부족 시 예외 발생
        val exception = assertThrows<IllegalArgumentException> {
            pointService.usePoint(userId, useAmount)
        }
        assertEquals("잔액이 부족합니다", exception.message)

        // Mock 검증: 잔액 확인 후 실패하므로 업데이트 관련 메서드들은 호출되지 않아야 함
        verify(userPointTable).selectById(userId)
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong())
        verifyNoMoreInteractions(pointHistoryTable)
    }

    /**
     * 테스트 케이스: 유효하지 않은 사용 금액에 대한 예외 처리
     *
     * 작성 이유:
     * - 비즈니스 규칙 위반에 대한 예외 처리 검증 (0원 이하 사용 금지)
     * - 잘못된 입력값에 대한 방어적 프로그래밍 구현 확인
     * - 포인트 사용 전 유효성 검증이 우선적으로 수행되는지 확인
     * - 예외 발생 시 데이터베이스 작업이 수행되지 않는지 확인 (원자성 보장)
     *
     * 검증 내용:
     * - 0원과 음수 금액에 대해 IllegalArgumentException 발생 확인
     * - 예외 메시지의 정확성 검증
     * - 예외 발생 시 다른 메서드들이 호출되지 않았는지 Mock 검증
     */
    @Test
    fun `0 이하의 금액으로 사용 시도하면 예외가 발생한다`() {
        // Given: 유효하지 않은 사용 금액들
        val userId = 1L
        val zeroAmount = 0L
        val negativeAmount = -100L

        // When & Then: 0원 사용 시도
        val zeroException = assertThrows<IllegalArgumentException> {
            pointService.usePoint(userId, zeroAmount)
        }
        assertEquals("사용 금액은 0보다 커야 합니다", zeroException.message)

        // When & Then: 음수 금액 사용 시도
        val negativeException = assertThrows<IllegalArgumentException> {
            pointService.usePoint(userId, negativeAmount)
        }
        assertEquals("사용 금액은 0보다 커야 합니다", negativeException.message)

        // Mock 검증: 유효성 검사에서 실패하므로 다른 메서드들은 호출되지 않아야 함
        verifyNoMoreInteractions(userPointTable)
        verifyNoMoreInteractions(pointHistoryTable)
    }

    /**
     * 테스트 케이스: 포인트 조회 실패 시 예외 전파 확인
     *
     * 작성 이유:
     * - 의존성(UserPointTable)에서 발생하는 예외의 전파 동작 검증
     * - 예외 상황에서의 시스템 안정성과 일관성 확인
     * - 데이터베이스 연결 실패 등 하위 계층 오류에 대한 처리 검증
     * - 예외 발생 시 후속 작업이 수행되지 않는지 확인 (원자성 보장)
     *
     * 검증 내용:
     * - 하위 계층에서 발생한 예외가 그대로 전파되는지 확인
     * - 예외 메시지가 보존되는지 확인
     * - 예외 발생 시 후속 메서드들이 호출되지 않았는지 Mock 검증
     */
    @Test
    fun `포인트 충전 시 현재 포인트 조회가 실패하면 예외가 전파된다`() {
        // Given: 포인트 조회 시 예외 발생
        val userId = 1L
        val chargeAmount = 1000L
        val expectedException = RuntimeException("Database connection failed")

        // Stub: userPointTable.selectById에서 예외 발생하도록 설정
        `when`(userPointTable.selectById(userId)).thenThrow(expectedException)

        // When & Then: 예외가 전파되는지 확인
        val thrownException = assertThrows<RuntimeException> {
            pointService.chargePoint(userId, chargeAmount)
        }
        assertEquals("Database connection failed", thrownException.message)

        // Mock 검증: selectById는 호출되지만 다른 메서드들은 호출되지 않아야 함
        verify(userPointTable).selectById(userId)
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong())
        verifyNoMoreInteractions(pointHistoryTable)
    }

    /**
     * 테스트 케이스: 포인트 사용 시 조회 실패에 대한 예외 전파 확인
     *
     * 작성 이유:
     * - 포인트 사용 과정에서 현재 잔액 조회 실패 시의 예외 처리 검증
     * - 의존성(UserPointTable)에서 발생하는 예외의 전파 동작 확인
     * - 데이터베이스 연결 실패 등 인프라 레벨 오류에 대한 처리 검증
     * - 예외 발생 시 트랜잭션의 원자성이 보장되는지 확인
     *
     * 검증 내용:
     * - 하위 계층에서 발생한 예외가 그대로 전파되는지 확인
     * - 예외 메시지가 보존되는지 확인
     * - 예외 발생 시 후속 메서드들이 호출되지 않았는지 Mock 검증
     */
    @Test
    fun `포인트 사용 시 현재 포인트 조회가 실패하면 예외가 전파된다`() {
        // Given: 포인트 조회 시 예외 발생
        val userId = 1L
        val useAmount = 500L
        val expectedException = RuntimeException("Database connection failed")

        // Stub: userPointTable.selectById에서 예외 발생하도록 설정
        `when`(userPointTable.selectById(userId)).thenThrow(expectedException)

        // When & Then: 예외가 전파되는지 확인
        val thrownException = assertThrows<RuntimeException> {
            pointService.usePoint(userId, useAmount)
        }
        assertEquals("Database connection failed", thrownException.message)

        // Mock 검증: selectById는 호출되지만 다른 메서드들은 호출되지 않아야 함
        verify(userPointTable).selectById(userId)
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong())
        verifyNoMoreInteractions(pointHistoryTable)
    }

    /**
     * 테스트 케이스: 포인트 업데이트 실패 시 히스토리 저장 방지 확인
     *
     * 작성 이유:
     * - 트랜잭션의 원자성 보장 검증 (포인트 업데이트 실패 시 히스토리도 저장되지 않음)
     * - 데이터 일관성 유지를 위한 중요한 비즈니스 로직 테스트
     * - 부분적 실패 상황에서의 시스템 동작 검증
     * - 예외 발생 시 롤백 동작의 정확성 확인
     *
     * 검증 내용:
     * - 포인트 업데이트 실패 시 예외가 전파되는지 확인
     * - 예외 발생 시 히스토리 저장 메서드가 호출되지 않았는지 Mock 검증
     * - 트랜잭션의 부분 성공 방지를 통한 데이터 무결성 보장 확인
     */
    @Test
    fun `포인트 업데이트가 실패하면 히스토리는 저장되지 않는다`() {
        // Given: 포인트 업데이트 시 예외 발생
        val userId = 1L
        val chargeAmount = 1000L
        val currentPoint = 500L
        val expectedNewPoint = currentPoint + chargeAmount
        val currentUserPoint = UserPoint(userId, currentPoint, System.currentTimeMillis())
        val expectedException = RuntimeException("Update failed")

        // Stub: selectById는 성공하지만 insertOrUpdate에서 예외 발생
        `when`(userPointTable.selectById(userId)).thenReturn(currentUserPoint)
        `when`(userPointTable.insertOrUpdate(userId, expectedNewPoint)).thenThrow(expectedException)

        // When & Then: 예외가 전파되는지 확인
        val thrownException = assertThrows<RuntimeException> {
            pointService.chargePoint(userId, chargeAmount)
        }
        assertEquals("Update failed", thrownException.message)

        // Mock 검증: 업데이트 실패 시 히스토리는 저장되지 않아야 함
        verify(userPointTable).selectById(userId)
        verify(userPointTable).insertOrUpdate(userId, expectedNewPoint)
        verifyNoMoreInteractions(pointHistoryTable)
    }
}