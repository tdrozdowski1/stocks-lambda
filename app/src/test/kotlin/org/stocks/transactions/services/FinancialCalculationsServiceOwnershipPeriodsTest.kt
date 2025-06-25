package org.stocks.transactions.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.stocks.transactions.OwnershipPeriod
import org.stocks.transactions.Transaction
import java.math.BigDecimal

class FinancialCalculationsServiceOwnershipPeriodsTest {

    private val financialCalculationsService = FinancialCalculationsService()

    @Test
    fun `calculateMoneyInvested should calculate correct money invested`() {
        val transactions = listOf(
            Transaction("AAPL", "2023-01-01", "buy", BigDecimal("10"), BigDecimal("150"), BigDecimal("5")),
            Transaction("AAPL", "2023-02-01", "sell", BigDecimal("5"), BigDecimal("160"), BigDecimal("3")),
            Transaction("AAPL", "2023-03-01", "buy", BigDecimal("8"), BigDecimal("140"), BigDecimal("4"))
        )

        val result = financialCalculationsService.calculateMoneyInvested(transactions)

        assertEquals(BigDecimal("1832"), result)
    }

    @Test
    fun `calculateOwnershipPeriods should calculate correct ownership periods`() {
        val transactions = listOf(
            Transaction("V", "2023-01-01", "buy", BigDecimal("10"), BigDecimal("100"), BigDecimal("5")),
            Transaction("V", "2023-02-01","sell", BigDecimal("5"), BigDecimal("120"), BigDecimal("3")),
            Transaction("V", "2023-03-01","buy", BigDecimal("5"), BigDecimal("110"), BigDecimal("2"))
        )

        val result = financialCalculationsService.calculateOwnershipPeriods(transactions)

        val expected = listOf(
            OwnershipPeriod("2023-01-01", "2023-02-01", BigDecimal("10")),
            OwnershipPeriod("2023-02-01", "2023-03-01", BigDecimal("5")),
            OwnershipPeriod("2023-03-01", null, BigDecimal("10"))
        )

        assertEquals(expected, result)
    }
}