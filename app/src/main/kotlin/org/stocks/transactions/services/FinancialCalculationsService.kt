package org.stocks.transactions.services

import org.stocks.transactions.OwnershipPeriod
import org.stocks.transactions.Transaction
import java.math.BigDecimal
import java.time.LocalDate

class FinancialCalculationsService {

    fun calculateMoneyInvested(transactions: List<Transaction>): BigDecimal {
        var totalBuy = BigDecimal.ZERO
        var totalSell = BigDecimal.ZERO
        var commission = BigDecimal.ZERO

        transactions.forEach { t ->
            commission = commission.add(t.commission)
            when (t.type) {
                "buy" -> totalBuy = totalBuy.add(t.amount.multiply(t.price))
                "sell" -> totalSell = totalSell.add(t.amount.multiply(t.price))
            }
        }
        return totalBuy.subtract(totalSell).add(commission)
    }

    fun calculateOwnershipPeriods(transactions: List<Transaction>): List<OwnershipPeriod> {
        //TODO: Are transactions requried if we already have OwnershipPeriods?
        val ownershipPeriods = mutableListOf<OwnershipPeriod>()
        var totalAmount = BigDecimal.ZERO
        var startDate: LocalDate? = null

        transactions.forEach { t ->
            val transactionDate = LocalDate.parse(t.date)
            when (t.type) {
                "buy" -> {
                    if (totalAmount > BigDecimal.ZERO && startDate != null) {
                        ownershipPeriods.add(
                            OwnershipPeriod(startDate.toString(), transactionDate.toString(), totalAmount)
                        )
                    }
                    totalAmount = totalAmount.add(t.amount)
                    startDate = transactionDate
                }
                "sell" -> {
                    if (totalAmount > BigDecimal.ZERO && startDate != null) {
                        ownershipPeriods.add(
                            OwnershipPeriod(startDate.toString(), transactionDate.toString(), totalAmount)
                        )
                        totalAmount = totalAmount.subtract(t.amount)
                        startDate = if (totalAmount > BigDecimal.ZERO) transactionDate else null
                    }
                }
            }
        }

        if (totalAmount > BigDecimal.ZERO && startDate != null) {
            ownershipPeriods.add(OwnershipPeriod(startDate.toString(), null, totalAmount))
        }

        return ownershipPeriods
    }
}