package org.stocks.transactions

import java.math.BigDecimal

data class Stock(
    val symbol: String,
    val email : String,
    val transactions: List<Transaction>,
    val moneyInvested: BigDecimal,
    val ownershipPeriods: List<OwnershipPeriod>,
    val currentPrice: BigDecimal? = null,
    val dividends: List<DividendDetail>? = null,
    val totalDividendValue: BigDecimal? = null,
    val cashFlowData: List<CashFlowData>? = null,
    val liabilitiesData: List<LiabilitiesData>? = null,
    val totalWithholdingTaxPaid: BigDecimal? = null,
    val taxToBePaidInPoland: BigDecimal? = null
)

data class OwnershipPeriod(val startDate: String, val endDate: String?, val quantity: BigDecimal)

data class Transaction(val symbol: String, val date: String, val type: String, val amount: BigDecimal, val price: BigDecimal, val commission: BigDecimal)

data class DividendDetail(
    val date: String,
    val label: String,
    val adjDividend: BigDecimal,
    val dividend: BigDecimal,
    val recordDate: String,
    val paymentDate: String,
    val declarationDate: String,
    val quantity: BigDecimal? = null,
    val totalDividend: BigDecimal? = null,
    val usdPlnRate: BigDecimal? = null,
    val withholdingTaxPaid: BigDecimal? = null,
    val dividendInPln: BigDecimal? = null,
    val taxDueInPoland: BigDecimal? = null,
    val currency: String = "USD"
)

data class CashFlowData(val date: String, val dividendsPaid: BigDecimal, val freeCashFlow: BigDecimal)

data class LiabilitiesData(val date: String, val totalLiabilities: BigDecimal, val totalAssets: BigDecimal, val totalEquity: BigDecimal, val totalDebt: BigDecimal)