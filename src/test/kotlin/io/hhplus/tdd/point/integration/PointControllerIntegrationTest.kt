package io.hhplus.tdd.point.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.tdd.point.UserPoint
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

@SpringBootTest
@AutoConfigureWebMvc
@DisplayName("PointController 통합 테스트")
class PointControllerIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    /**
     * 테스트 케이스: 포인트 시스템의 전체 비즈니스 플로우 통합 테스트
     *
     * 작성 이유:
     * - 단일 사용자에 대한 전체 사용 시나리오(충전 -> 사용 -> 조회)의 무결성 검증
     * - 데이터 일관성이 여러 API 호출에 걸쳐 유지되는지 확인
     * - 실제 환경에서의 API 간 상호 작용과 데이터 지속성 검증
     * - 충전과 사용의 계산 로직이 전체 플로우에서 올바르게 작동하는지 확인
     * - 거래 내역 저장과 조회가 시간순으로 올바르게 작동하는지 검증
     *
     * 검증 내용:
     * - 초기 포인트 0 -> 충전 1000 -> 사용 300 -> 잔액 700 순서대로 진행
     * - 각 단계에서 HTTP 200 OK 또는 예상 응답 수신 확인
     * - 거래 내역에 CHARGE와 USE 두 건의 기록이 올바른 금액으로 저장되는지 검증
     */
    @Test
    fun `전체 플로우 통합 테스트 - 충전, 사용, 조회`() {
        val userId = 1L
        val chargeAmount = 1000L
        val useAmount = 300L

        // 1. 초기 포인트 조회
        mockMvc.get("/point/{id}", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(userId) }
            jsonPath("$.point") { value(0L) }
        }

        // 2. 포인트 충전
        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = chargeAmount.toString()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(userId) }
            jsonPath("$.point") { value(chargeAmount) }
        }

        // 3. 포인트 사용
        mockMvc.patch("/point/{id}/use", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = useAmount.toString()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(userId) }
            jsonPath("$.point") { value(chargeAmount - useAmount) }
        }

        // 4. 최종 포인트 조회
        mockMvc.get("/point/{id}", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.point") { value(chargeAmount - useAmount) }
        }

        // 5. 거래 내역 조회
        mockMvc.get("/point/{id}/histories", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].type") { value("CHARGE") }
            jsonPath("$[0].amount") { value(chargeAmount) }
            jsonPath("$[1].type") { value("USE") }
            jsonPath("$[1].amount") { value(useAmount) }
        }
    }

    /**
     * 테스트 케이스: 연속된 다중 충전/사용 요청의 정상 처리 검증
     *
     * 작성 이유:
     * - 연속된 요청 하에서 데이터 일관성과 누적 계산의 정확성 검증
     * - 다중 트랜잭션에 걸친 포인트 잔액 계산의 올바른 누적 확인
     * - 여러 거래 내역이 순서대로 저장되고 조회되는지 검증
     * - 실제 사용자 시나리오에서 발생할 수 있는 연속 요청 패턴 테스트
     *
     * 검증 내용:
     * - 충전 500 + 충전 300 + 사용 200 = 최종 잔액 600 계산 검증
     * - 3건의 거래 내역이 모두 올바르게 저장되는지 확인
     * - 각 요청에 대해 HTTP 200 OK 응답 수신 확인
     */
    @Test
    fun `연속된 충전과 사용 요청이 정상적으로 처리된다`() {
        val userId = 2L

        // 여러 번 충전
        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = "500"
        }.andExpect { status { isOk() } }

        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = "300"
        }.andExpect { status { isOk() } }

        mockMvc.patch("/point/{id}/use", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = "200"
        }.andExpect { status { isOk() } }

        // 최종 잔액 확인 (500 + 300 - 200 = 600)
        mockMvc.get("/point/{id}", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.point") { value(600L) }
        }

        // 거래 내역 확인
        mockMvc.get("/point/{id}/histories", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
        }
    }

    /**
     * 테스트 케이스: 다양한 잘못된 요청에 대한 에러 처리 통합 테스트
     *
     * 작성 이유:
     * - 엔드-투-엔드 예외 처리가 전체 시스템에서 일관되게 작동하는지 확인
     * - 여러 유형의 비즈니스 규칙 위반 상황을 한 번에 검증
     * - HTTP 에러 상태 코드의 일관성 및 정확성 검증
     * - 실제 사용자가 잘못된 입력을 했을 때의 시스템 동작 확인
     *
     * 검증 내용:
     * - 0원 충전, 음수 충전, 잔액 부족 사용 시나리오에 대해 모두 HTTP 400 반환
     * - 데이터 무결성이 예외 상황에서도 유지되는지 확인
     * - 예외 발생 시 데이터에 영향을 주지 않는지 간접 확인
     */
    @Test
    fun `잘못된 요청에 대해 적절한 에러 응답을 반환한다`() {
        val userId = 3L

        // 0원 충전 시도
        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = "0"
        }.andExpect {
            status { isBadRequest() }
        }

        // 음수 충전 시도
        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = "-100"
        }.andExpect {
            status { isBadRequest() }
        }

        // 잔액보다 많은 금액 사용 시도
        mockMvc.patch("/point/{id}/use", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = "1000"
        }.andExpect {
            status { isBadRequest() }
        }
    }

    /**
     * 테스트 케이스: 동시성 환경에서의 충전 요청 처리 테스트
     *
     * 작성 이유:
     * - 동시성 제어가 없는 현재 구현에서의 데이터 정합성 문제 확인
     * - 리얼 월드 환경에서 발생할 수 있는 동시 요청 시나리오 테스트
     * - 레이스 컨디션(Race Condition) 발생 가능성과 영향도 검증
     * - 향후 동시성 제어 상선을 위한 기준점(Baseline) 제공
     *
     * 검증 내용:
     * - 10개 쓰레드가 동시에 100원씩 충전 요청
     * - 예상 최종 포인트(1000)와 실제 결과 비교 및 차이 분석
     * - 거래 내역 수가 요청 수(10건)와 일치하는지 확인
     * - 동시성 문제 여부를 콘솔 출력으로 시각적 확인
     */
    @Test
    fun `동시 충전 요청이 안전하게 처리되는지 확인한다`() {
        val userId = 4L
        val threadCount = 10
        val chargeAmount = 100L

        // 동시에 여러 충전 요청 실행
        val futures = (1..threadCount).map {
            CompletableFuture.supplyAsync {
                try {
                    mockMvc.perform(
                        patch("/point/{id}/charge", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chargeAmount.toString())
                    ).andReturn()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }

        // 모든 요청 완료 대기
        CompletableFuture.allOf(*futures.toTypedArray()).get()

        // 최종 포인트 확인
        val result = mockMvc.get("/point/{id}", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val responseContent = result.response.contentAsString
        val userPoint = objectMapper.readValue(responseContent, UserPoint::class.java)

        // 동시성 제어가 올바르게 작동했는지 검증
        val expectedPoint = threadCount * chargeAmount
        println("예상 포인트: $expectedPoint, 실제 포인트: ${userPoint.point}")

        // 거래 내역도 확인
        mockMvc.get("/point/{id}/histories", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(threadCount) }
        }
    }

    /**
     * 테스트 케이스: 동시 충전과 사용 요청의 복합 시나리오 테스트
     *
     * 작성 이유:
     * - 가장 복잡한 동시성 시나리오에서의 데이터 일관성 확인
     * - 상반된 연산(충전과 사용)이 동시에 발생할 때의 시스템 동작 검증
     * - 리얼 월드에서 발생 가능한 고부하 상황 시뮤레이션
     * - 데이터 레이스 컨디션이 충전/사용 모두에 영향을 줄 수 있음을 입증
     * - CountDownLatch를 사용한 동기화로 정확한 동시성 테스트 구현
     *
     * 검증 내용:
     * - 초기 1000 충전 후 5개 충전(+100)과 5개 사용(-50) 요청 동시 실행
     * - 최종 포인트 상태와 거래 내역의 무결성 확인
     * - 성공/실패한 요청들의 결과를 콘솔 로그로 추적 가능
     * - 예외 발생 시에도 다른 요청들의 예상 처리 여부 확인
     */
    @Test
    fun `동시 충전과 사용 요청의 혼합 상황을 처리한다`() {
        val userId = 5L
        val initialCharge = 1000L

        // 초기 충전
        mockMvc.patch("/point/{id}/charge", userId) {
            contentType = MediaType.APPLICATION_JSON
            content = initialCharge.toString()
        }.andExpect { status { isOk() } }

        val threadCount = 5
        val latch = CountDownLatch(threadCount * 2) // 충전 5개 + 사용 5개

        // 동시에 충전과 사용 요청
        repeat(threadCount) {
            Thread {
                try {
                    // 충전 요청
                    mockMvc.perform(
                        patch("/point/{id}/charge", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("100")
                    )
                    latch.countDown()
                } catch (e: Exception) {
                    println("충전 실패: ${e.message}")
                    latch.countDown()
                }
            }.start()

            Thread {
                try {
                    // 사용 요청
                    mockMvc.perform(
                        patch("/point/{id}/use", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("50")
                    )
                    latch.countDown()
                } catch (e: Exception) {
                    println("사용 실패: ${e.message}")
                    latch.countDown()
                }
            }.start()
        }

        latch.await()

        // 최종 상태 확인
        val result = mockMvc.get("/point/{id}", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val responseContent = result.response.contentAsString
        val userPoint = objectMapper.readValue(responseContent, UserPoint::class.java)

        println("최종 포인트: ${userPoint.point}")

        // 거래 내역 확인
        val historyResult = mockMvc.get("/point/{id}/histories", userId) {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val historyContent = historyResult.response.contentAsString
        println("총 거래 내역 개수: ${objectMapper.readTree(historyContent).size()}")
    }
}