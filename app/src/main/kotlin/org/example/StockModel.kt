data class Stock(
    val symbol: String,
    val moneyInvested: Double,
    val currentPrice: List<CurrentPriceData>,
    val ownershipPeriods: List<OwnershipPeriod>,
    val transactions: List<Transaction>,
    val dividends: List<DividendDetail>?,
    val totalDividendValue: Double,
    val cashFlowData: List<CashFlowData>?,
    val liabilitiesData: List<LiabilitiesData>?,
    var totalWithholdingTaxPaid: Double?,
    var taxToBePaidInPoland: Double?
)

data class CurrentPriceData(
    val symbol: String,
    val name: String,
    val price: Double,
    val changesPercentage: Double,
    val change: Double,
    val dayLow: Double,
    val dayHigh: Double,
    val yearHigh: Double,
    val yearLow: Double,
    val marketCap: Long,
    val priceAvg50: Double,
    val priceAvg200: Double,
    val exchange: String,
    val volume: Long,
    val avgVolume: Long,
    val open: Double,
    val previousClose: Double,
    val eps: Double,
    val pe: Double,
    val earningsAnnouncement: String,
    val sharesOutstanding: Long,
    val timestamp: Long
)

data class OwnershipPeriod(val startDate: String, val endDate: String?, val quantity: Double)
data class Transaction(val symbol: String, val date: String, val type: String, val amount: Double, val price: Double, val commission: Double)
data class DividendDetail(
    val date: String,
    val label: String,
    val adjDividend: Double,
    val dividend: Double,
    val recordDate: String,
    val paymentDate: String,
    val declarationDate: String,
    val quantity: Double,
    val totalDividend: Double,
    var usdPlnRate: Double,
    var withholdingTaxPaid: Double,
    var dividendInPln: Double,
    var taxDueInPoland: Double
)
data class CashFlowData(val date: String, val dividendsPaid: Double, val freeCashFlow: Double)
data class LiabilitiesData(val date: String, val totalLiabilities: Double, val totalAssets: Double, val totalEquity: Double, val totalDebt: Double)
