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
) : RequestHandler<Map<String, Any>, Map<String, Any>> {

    private val apiKey = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D"
    private val baseUrl = "https://financialmodelingprep.com/api/v3"

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, Any> {
        val method = input["httpMethod"] as? String ?: "GET"

        if (method == "OPTIONS") {
            // Handle CORS Preflight Request
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        val transactionJson = input["body"] as? String
            ?: return buildCorsResponse(400, "Error: No transaction body provided")

        val transaction = objectMapper.readValue<Transaction>(transactionJson)
        val stock = addTransaction(transaction)
        val responseBody = objectMapper.writeValueAsString(stock)

        return buildCorsResponse(200, responseBody)
    }

    private fun buildCorsResponse(statusCode: Int, body: String): Map<String, Any> {
        return mapOf(
            "statusCode" to statusCode,
            "headers" to corsHeaders(),
            "body" to body
        )
    }

    private fun addTransaction(transaction: Transaction): Stock {
        val currentStocks = dbService.getStocks()
        var stock = currentStocks.find { it.symbol == transaction.symbol }

        if (stock != null) {
            val updatedTransactions = stock.transactions + transaction
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
                currentPrice = emptyList(),
                ownershipPeriods = calculateOwnershipPeriods(listOf(transaction)),
                dividends = null,
                totalDividendValue = 0.0,
                cashFlowData = null,
                liabilitiesData = null,
                taxToBePaidInPoland = null,
                totalWithholdingTaxPaid = null
            )
        }

        stock = stock.copy(currentPrice = getStockPrice(stock.symbol))

        val dividendsData = getDividends(stock.symbol)
        val ownershipPeriods = calculateOwnershipPeriods(stock.transactions)
        stock.dividends = dividendService.filterDividendsByOwnership(dividendsData, ownershipPeriods)
        stock.totalDividendValue = dividendService.calculateTotalDividends(stock.dividends!!)

        stock = dividendService.updateUsdPlnRateForDividends(stock)
        stock = dividendService.calculateTaxToBePaidInPoland(stock)
        stock = dividendService.calculateTotalWithholdingTaxPaid(stock)

        dbService.updateStock(stock)
        return stock
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
                            OwnershipPeriod(startDate.toString(), transactionDate.toString(), totalAmount)
                        )
                    }
                    totalAmount += t.amount
                    startDate = transactionDate
                }
                "sell" -> {
                    if (totalAmount > 0 && startDate != null) {
                        ownershipPeriods.add(
                            OwnershipPeriod(startDate.toString(), transactionDate.toString(), totalAmount)
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

    private fun corsHeaders(): Map<String, String> {
        return mapOf(
            "Access-Control-Allow-Origin" to "https://main.d2nn1tu89v11eh.amplifyapp.com",
            "Access-Control-Allow-Methods" to "OPTIONS, POST",
            "Access-Control-Allow-Headers" to "Content-Type, X-Amz-Date, Authorization, X-Api-Key, X-Amz-Security-Token"
        )
    }
}
