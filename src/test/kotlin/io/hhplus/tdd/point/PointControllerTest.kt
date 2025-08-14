package io.hhplus.tdd.point

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*

@WebMvcTest(PointController::class)
@DisplayName("PointController 단위 테스트")
class PointControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var pointService: PointService

    /**
     * 테스트 케이스: GET /point/{id} 엔드포인트를 통한 사용자 포인트 조회 성공
     *
     * 작성 이유:
     * - REST API 엔드포인트의 정상 동작 검증
     * - HTTP GET 요청에 대한 컸트롤러 레이어의 랑핑과 응답 처리 확인
     * - PointService와의 연동이 정상적으로 작동하는지 검증
     * - JSON 직렬화와 HTTP 상태 코드 반환이 올바른지 확인
     *
     * 검증 내용:
     * - HTTP 200 OK 상태 코드 반환 확인
     * - Content-Type이 application/json인지 확인
     * - 응답 JSON의 id, point 필드가 예상값과 일치하는지 검증
     * - PointService.getUserPoint 메서드가 올바른 파라미터로 호출되었는지 Mock 검증
     */
    @Test
    fun `GET point - 특정 사용자의 포인트를 조회한다`() {
        val userId = 1L
        val expectedPoint = UserPoint(userId, 1000L, System.currentTimeMillis())

        `when`(pointService.getUserPoint(userId)).thenReturn(expectedPoint)

        mockMvc.get("/point/{id}", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(userId) }
            jsonPath("$.point") { value(1000L) }
        }

        verify(pointService).getUserPoint(userId)
    }

    /**
     * 테스트 케이스: GET /point/{id}/histories 엔드포인트를 통한 포인트 거래 내역 조회 성공
     *
     * 작성 이유:
     * - 포인트 거래 내역 조회 API의 정상 동작 검증
     * - 배열 타입의 JSON 응답 처리와 직렬화 확인
     * - 여러 거래 내역(충전, 사용)의 올바른 반환 검증
     * - REST API 설계 원칙에 따른 엔드포인트 동작 확인
     *
     * 검증 내용:
     * - HTTP 200 OK 상태 코드와 JSON Content-Type 반환 확인
     * - 응답 배열의 길이와 각 요소의 필드값 검증
     * - 거래 타입(CHARGE, USE)과 금액 정보의 정확성 확인
     * - PointService.getPointHistory 메서드 호출 및 파라미터 검증
     */
    @Test
    fun `GET histories - 특정 사용자의 포인트 내역을 조회한다`() {
        val userId = 1L
        val expectedHistory = listOf(
            PointHistory(1L, userId, TransactionType.CHARGE, 1000L, System.currentTimeMillis()),
            PointHistory(2L, userId, TransactionType.USE, 500L, System.currentTimeMillis())
        )

        `when`(pointService.getPointHistory(userId)).thenReturn(expectedHistory)

        mockMvc.get("/point/{id}/histories", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].userId") { value(userId) }
            jsonPath("$[0].type") { value("CHARGE") }
            jsonPath("$[0].amount") { value(1000L) }
            jsonPath("$[1].type") { value("USE") }
            jsonPath("$[1].amount") { value(500L) }
        }

        verify(pointService).getPointHistory(userId)
    }

    /**
     * 테스트 케이스: PATCH /point/{id}/charge 엔드포인트를 통한 포인트 충전 성공
     *
     * 작성 이유:
     * - HTTP PATCH 메서드를 사용한 포인트 충전 API의 정상 동작 검증
     * - 요청 본문(Request Body)의 수치 데이터 처리 확인
     * - 충전 후 업데이트된 포인트 정보의 올바른 반환 검증
     * - REST API의 상태 변경 연산에 따른 PATCH 메서드 사용 정당성 확인
     *
     * 검증 내용:
     * - HTTP 200 OK 상태 코드와 JSON Content-Type 반환 확인
     * - 응답 JSON의 id, point 필드가 충전 결과와 일치하는지 검증
     * - PointService.chargePoint 메서드가 올바른 파라미터로 호출되었는지 Mock 검증
     * - 요청 본문의 수치 데이터가 올바르게 파싱되었는지 확인
     */
    @Test
    fun `PATCH charge - 포인트를 정상적으로 충전한다`() {
        val userId = 1L
        val chargeAmount = 1000L
        val expectedResult = UserPoint(userId, 1000L, System.currentTimeMillis())

        `when`(pointService.chargePoint(userId, chargeAmount)).thenReturn(expectedResult)

        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = chargeAmount.toString()
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(userId) }
            jsonPath("$.point") { value(1000L) }
        }

        verify(pointService).chargePoint(userId, chargeAmount)
    }

    /**
     * 테스트 케이스: PATCH /point/{id}/charge 엔드포인트에서 잘못된 충전 금액 예외 처리
     *
     * 작성 이유:
     * - 잘못된 입력에 대한 HTTP 에러 응답 처리 검증
     * - 비즈니스 예외(IllegalArgumentException)에 대한 API 레벨에서의 적절한 에러 처리 확인
     * - 클라이언트가 올바른 에러 상태를 인식할 수 있도록 HTTP 상태 코드 반환 검증
     * - GlobalExceptionHandler 등의 예외 처리 로직이 정상 동작하는지 확인
     *
     * 검증 내용:
     * - HTTP 400 Bad Request 상태 코드 반환 확인
     * - PointService에서 발생한 IllegalArgumentException에 대한 올바른 처리 검증
     * - 음수 금액에 대한 유횤성 검사가 API 레벨에서도 작동하는지 확인
     */
    @Test
    fun `PATCH charge - 잘못된 금액으로 충전 시 예외를 반환한다`() {
        val userId = 1L
        val invalidAmount = -100L

        `when`(pointService.chargePoint(userId, invalidAmount))
            .thenThrow(IllegalArgumentException("충전 금액은 0보다 커야 합니다"))

        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = invalidAmount.toString()
        }.andExpect {
            status { isBadRequest() }
        }

        verify(pointService).chargePoint(userId, invalidAmount)
    }

    /**
     * 테스트 케이스: PATCH /point/{id}/use 엔드포인트를 통한 포인트 사용 성공
     *
     * 작성 이유:
     * - HTTP PATCH 메서드를 사용한 포인트 사용 API의 정상 동작 검증
     * - 사용 후 잔여 포인트가 올바르게 계산되어 반환되는지 확인
     * - 요청 본문의 수치 데이터 처리와 응답 JSON 직렬화 검증
     * - REST API의 리소스 상태 변경 연산에 따른 PATCH 메서드 사용의 적절성 확인
     *
     * 검증 내용:
     * - HTTP 200 OK 상태 코드와 JSON Content-Type 반환 확인
     * - 사용 후 잔여 포인트가 예상값과 일치하는지 응답 데이터 검증
     * - PointService.usePoint 메서드가 올바른 파라미터로 호출되었는지 Mock 검증
     */
    @Test
    fun `PATCH use - 포인트를 정상적으로 사용한다`() {
        val userId = 1L
        val useAmount = 500L
        val expectedResult = UserPoint(userId, 500L, System.currentTimeMillis())

        `when`(pointService.usePoint(userId, useAmount)).thenReturn(expectedResult)

        mockMvc.patch("/point/{id}/use", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = useAmount.toString()
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(userId) }
            jsonPath("$.point") { value(500L) }
        }

        verify(pointService).usePoint(userId, useAmount)
    }

    /**
     * 테스트 케이스: PATCH /point/{id}/use 엔드포인트에서 잔액 부족 예외 처리
     *
     * 작성 이유:
     * - 비즈니스 규칙 위반(잔액 부족)에 대한 HTTP 에러 응답 처리 검증
     * - 실제 금융 서비스에서 중요한 잔액 검증 로직의 API 레벨 동작 확인
     * - 클라이언트가 잘못된 요청의 원인을 명확히 인지할 수 있도록 에러 처리 검증
     * - HTTP 상태 코드를 통한 에러 유형 구분의 올바른 구현 확인
     *
     * 검증 내용:
     * - HTTP 400 Bad Request 상태 코드 반환 확인
     * - 잔액 부족 시 IllegalArgumentException이 올바른 HTTP 오류로 변환되는지 검증
     * - PointService.usePoint 메서드가 호출되는지 확인 (예외가 전파되더라도)
     */
    @Test
    fun `PATCH use - 잔액 부족 시 예외를 반환한다`() {
        val userId = 1L
        val useAmount = 1500L

        `when`(pointService.usePoint(userId, useAmount))
            .thenThrow(IllegalArgumentException("잔액이 부족합니다"))

        mockMvc.patch("/point/{id}/use", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = useAmount.toString()
        }.andExpect {
            status { isBadRequest() }
        }

        verify(pointService).usePoint(userId, useAmount)
    }

    /**
     * 테스트 케이스: PATCH /point/{id}/use 엔드포인트에서 잘못된 사용 금액 예외 처리
     *
     * 작성 이유:
     * - 0원 사용과 같은 잘못된 입력에 대한 예외 처리 검증
     * - 데이터 유효성 검사가 API 레벨에서도 올바르게 작동하는지 확인
     * - 비즈니스 로직 예외와 입력 유효성 예외의 일관된 처리 검증
     * - HTTP API에서의 예외 처리 및 상태 코드 매핑의 올바른 구현 확인
     *
     * 검증 내용:
     * - HTTP 400 Bad Request 상태 코드 반환 확인
     * - 0원 사용 시도에 대한 올바른 예외 발생 및 처리 검증
     * - PointService.usePoint 메서드가 호출되어 예외가 전파되는지 확인
     */
    @Test
    fun `PATCH use - 잘못된 금액으로 사용 시 예외를 반환한다`() {
        val userId = 1L
        val invalidAmount = 0L

        `when`(pointService.usePoint(userId, invalidAmount))
            .thenThrow(IllegalArgumentException("사용 금액은 0보다 커야 합니다"))

        mockMvc.patch("/point/{id}/use", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = invalidAmount.toString()
        }.andExpect {
            status { isBadRequest() }
        }

        verify(pointService).usePoint(userId, invalidAmount)
    }

    /**
     * 테스트 케이스: GET /point/{id} 엔드포인트에서 존재하지 않는 사용자 처리
     *
     * 작성 이유:
     * - 신규 사용자나 데이터가 없는 사용자에 대한 API 동작 검증
     * - 404 Not Found 반환이 아닌 기본값 반환 정책의 올바른 구현 확인
     * - 클라이언트 친화적인 API 설계 원칙의 적용 검증
     * - 연속된 API 호출에서 예외 상황이 발생하지 않도록 하는 안정성 확인
     *
     * 검증 내용:
     * - HTTP 200 OK 상태 코드 반환 확인 (404 아님)
     * - 빈 사용자에 대한 기본 포인트 0을 반환하는지 검증
     * - PointService.getUserPoint 메서드가 올바른 파라미터로 호출되었는지 확인
     */
    @Test
    fun `GET point - 존재하지 않는 사용자 조회 시 기본값을 반환한다`() {
        val nonExistentUserId = 999L
        val defaultPoint = UserPoint(nonExistentUserId, 0L, System.currentTimeMillis())

        `when`(pointService.getUserPoint(nonExistentUserId)).thenReturn(defaultPoint)

        mockMvc.get("/point/{id}", nonExistentUserId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(nonExistentUserId) }
            jsonPath("$.point") { value(0L) }
        }

        verify(pointService).getUserPoint(nonExistentUserId)
    }

    /**
     * 테스트 케이스: GET /point/{id}/histories 엔드포인트에서 내역이 없는 사용자 처리
     *
     * 작성 이유:
     * - 거래 내역이 없는 사용자(신규 또는 비활성 사용자)에 대한 API 동작 검증
     * - 빈 리스트 반환을 통한 일관된 API 인터페이스 제공 확인
     * - null 반환이 아닌 빈 배열 반환으로 NPE 방지 정책 확인
     * - 클라이언트에서 추가 null 처리 없이 바로 사용 가능한 API 설계 검증
     *
     * 검증 내용:
     * - HTTP 200 OK 상태 코드와 JSON Content-Type 반환 확인
     * - 빈 배열(길이 0)이 반환되는지 검증
     * - PointService.getPointHistory 메서드가 호출되어 빈 리스트를 올바르게 처리하는지 확인
     */
    @Test
    fun `GET histories - 내역이 없는 사용자 조회 시 빈 배열을 반환한다`() {
        val userId = 1L
        val emptyHistory = emptyList<PointHistory>()

        `when`(pointService.getPointHistory(userId)).thenReturn(emptyHistory)

        mockMvc.get("/point/{id}/histories", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.length()") { value(0) }
        }

        verify(pointService).getPointHistory(userId)
    }
}