package org.example.transactions

import CurrentPriceData
import DividendDetail
import OwnershipPeriod
import Transaction
import Stock
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

class TransactionLambdaHandler(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val dividendService: DividendService = DividendService(),
    private val dbService: DbService = DbService(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : RequestHandler<Map<String, Any>, String> {

    private val apiKey = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D"
    private val baseUrl = "https://financialmodelingprep.com/api/v3"

    override fun handleRequest(input: Map<String, Any>, context: Context): String {
        val transactionJson = input["body"] as? String ?: return "Error: No transaction body provided"
        val transaction = objectMapper.readValue<Transaction>(transactionJson)
        return addTransaction(transaction)
    }

    private fun addTransaction(transaction: Transaction): String {
        val currentStocks = dbService.getStocks()
        var stock = currentStocks.find { it.symbol == transaction.symbol }

        if (stock != null) {
            val updatedTransactions: List<Transaction> = stock.transactions + listOf(transaction)
            stock = stock.copy(
                transactions = updatedTransactions,
                moneyInvested = calculateMoneyInvested(updatedTransactions),
                ownershipPeriods = calculateOwnershipPeriods(updatedTransactions)
            )
        } else {
            stock = Stock(
                symbol = transaction.symbol,
                transactions = listOf(transaction),
                moneyInvested = calculateMoneyInvested(listOf(transaction)),
                currentPrice = emptyList(), // Will be updated below
                ownershipPeriods = calculateOwnershipPeriods(listOf(transaction)),
                dividends = null,           // Nullable, set to null initially
                totalDividendValue = 0.0,   // Non-nullable, default to 0.0
                cashFlowData = null,
                liabilitiesData = null,
                taxToBePaidInPoland = null, // Nullable, set to null initially
                totalWithholdingTaxPaid = null // Nullable, set to null initially
            )
        }

        // Fetch current stock price
        stock = stock.copy(currentPrice = getStockPrice(stock.symbol))

        // Fetch and process dividends using DividendService
        val dividendsData = getDividends(stock.symbol)
        val ownershipPeriods = calculateOwnershipPeriods(stock.transactions)
        stock.dividends = dividendService.filterDividendsByOwnership(dividendsData, ownershipPeriods)
        stock.totalDividendValue = dividendService.calculateTotalDividends(stock.dividends!!)

        // Update USD/PLN rates and taxes using DividendService
        stock = dividendService.updateUsdPlnRateForDividends(stock)
        stock = dividendService.calculateTaxToBePaidInPoland(stock)
        stock = dividendService.calculateTotalWithholdingTaxPaid(stock)

        // Save to DynamoDB using DbService
        dbService.updateStock(stock)

        return objectMapper.writeValueAsString(stock)
    }

    private fun calculateMoneyInvested(transactions: List<Transaction>): Double {
        var totalBuy = 0.0
        var totalSell = 0.0
        var commission = 0.0

        transactions.forEach { t ->
            commission += t.commission
            when (t.type) {
                "buy" -> totalBuy += t.amount * t.price
                "sell" -> totalSell += t.amount * t.price
            }
        }
        return totalBuy - totalSell + commission
    }

    private fun calculateOwnershipPeriods(transactions: List<Transaction>): List<OwnershipPeriod> {
        val ownershipPeriods = mutableListOf<OwnershipPeriod>()
        var totalAmount = 0.0
        var startDate: LocalDate? = null

        transactions.forEach { t ->
            val transactionDate = LocalDate.parse(t.date)
            when (t.type) {
                "buy" -> {
                    if (totalAmount > 0 && startDate != null) {
                        ownershipPeriods.add(
                            OwnershipPeriod(
                                startDate.toString(),
                                transactionDate.toString(),
                                totalAmount
                            )
                        )
                    }
                    totalAmount += t.amount
                    startDate = transactionDate
                }
                "sell" -> {
                    if (totalAmount > 0 && startDate != null) {
                        ownershipPeriods.add(
                            OwnershipPeriod(
                                startDate.toString(),
                                transactionDate.toString(),
                                totalAmount
                            )
                        )
                        totalAmount -= t.amount
                        startDate = if (totalAmount > 0) transactionDate else null
                    }
                }
            }
        }

        if (totalAmount > 0 && startDate != null) {
            ownershipPeriods.add(OwnershipPeriod(startDate.toString(), null, totalAmount))
        }

        return ownershipPeriods
    }

    private fun getStockPrice(symbol: String): List<CurrentPriceData> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/quote/$symbol?apikey=$apiKey"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return objectMapper.readValue(response.body())
    }

    private fun getDividends(symbol: String): List<DividendDetail> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/historical-price-full/stock_dividend/$symbol?apikey=$apiKey"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val json = objectMapper.readTree(response.body())
        return json["historical"].map {
            DividendDetail(
                date = it["date"].asText(),
                label = it["label"].asText(),
                adjDividend = it["adjDividend"].asDouble(),
                dividend = it["dividend"].asDouble(),
                recordDate = it["recordDate"].asText(),
                paymentDate = it["paymentDate"].asText(),
                declarationDate = it["declarationDate"].asText(),
                quantity = 0.0,
                totalDividend = 0.0,
                usdPlnRate = 0.0,
                withholdingTaxPaid = 0.0,
                dividendInPln = 0.0,
                taxDueInPoland = 0.0
            )
        }
    }
}