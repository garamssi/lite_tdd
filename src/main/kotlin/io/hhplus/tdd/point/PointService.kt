package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable
) {
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    fun getUserPoint(id: Long): UserPoint {
        return userPointTable.selectById(id)
    }

    fun getPointHistory(id: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(id)
    }

    fun chargePoint(id: Long, amount: Long): UserPoint {
        if (amount <= 0) {
            throw IllegalArgumentException("충전 금액은 0보다 커야 합니다")
        }

        val lock = userLocks.computeIfAbsent(id) { ReentrantLock() }
        return lock.withLock {
            val currentUser = userPointTable.selectById(id)
            val newAmount = currentUser.point + amount
            val updatedUser = userPointTable.insertOrUpdate(id, newAmount)

            pointHistoryTable.insert(
                id = id,
                amount = amount,
                transactionType = TransactionType.CHARGE,
                updateMillis = updatedUser.updateMillis
            )

            updatedUser
        }
    }

    fun usePoint(id: Long, amount: Long): UserPoint {
        if (amount <= 0) {
            throw IllegalArgumentException("사용 금액은 0보다 커야 합니다")
        }

        val lock = userLocks.computeIfAbsent(id) { ReentrantLock() }
        return lock.withLock {
            val currentUser = userPointTable.selectById(id)
            if (currentUser.point < amount) {
                throw IllegalArgumentException("잔액이 부족합니다")
            }

            val newAmount = currentUser.point - amount
            val updatedUser = userPointTable.insertOrUpdate(id, newAmount)

            pointHistoryTable.insert(
                id = id,
                amount = amount,
                transactionType = TransactionType.USE,
                updateMillis = updatedUser.updateMillis
            )

            updatedUser
        }
    }
}