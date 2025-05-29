import java.math.BigDecimal

data class Stock(
    val symbol: String,
    val moneyInvested: BigDecimal,
    val currentPrice: List<CurrentPriceData>,
    val ownershipPeriods: List<OwnershipPeriod>,
    val transactions: List<Transaction>,
    var dividends: List<DividendDetail>?,
    var totalDividendValue: BigDecimal,
    val cashFlowData: List<CashFlowData>?,
    val liabilitiesData: List<LiabilitiesData>?,
    var totalWithholdingTaxPaid: BigDecimal?,
    var taxToBePaidInPoland: BigDecimal?
)

data class CurrentPriceData(
    val symbol: String,
    val name: String,
    val price: BigDecimal,
    val changesPercentage: BigDecimal,
    val change: BigDecimal,
    val dayLow: BigDecimal,
    val dayHigh: BigDecimal,
    val yearHigh: BigDecimal,
    val yearLow: BigDecimal,
    val marketCap: Long,
    val priceAvg50: BigDecimal,
    val priceAvg200: BigDecimal,
    val exchange: String,
    val volume: Long,
    val avgVolume: Long,
    val open: BigDecimal,
    val previousClose: BigDecimal,
    val eps: BigDecimal,
    val pe: BigDecimal,
    val earningsAnnouncement: String,
    val sharesOutstanding: Long,
    val timestamp: Long
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
    val quantity: BigDecimal,
    val totalDividend: BigDecimal,
    var usdPlnRate: BigDecimal,
    var withholdingTaxPaid: BigDecimal,
    var dividendInPln: BigDecimal,
    var taxDueInPoland: BigDecimal
)
data class CashFlowData(val date: String, val dividendsPaid: BigDecimal, val freeCashFlow: BigDecimal)
data class LiabilitiesData(val date: String, val totalLiabilities: BigDecimal, val totalAssets: BigDecimal, val totalEquity: BigDecimal, val totalDebt: BigDecimal)
