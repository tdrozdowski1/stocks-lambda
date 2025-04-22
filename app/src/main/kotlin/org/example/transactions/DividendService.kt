package org.example.transactions

import DividendDetail
import OwnershipPeriod
import Stock
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DividendService(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val apiKey: String = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D",
    private val baseUrl: String = "https://financialmodelingprep.com/api/v3"
) {

    fun filterDividendsByOwnership(dividends: List<DividendDetail>, ownershipPeriods: List<OwnershipPeriod>): List<DividendDetail> {
        return dividends.filter { dividend ->
            try {
                val dividendDate = LocalDate.parse(dividend.date)
                ownershipPeriods.any { period ->
                    val start = LocalDate.parse(period.startDate)
                    val end = period.endDate?.let { LocalDate.parse(it) }
                    dividendDate >= start && (end == null || dividendDate <= end)
                }
            } catch (e: DateTimeParseException) {
                println("Skipping dividend with invalid date: '${dividend.date}'")
                false // Skip invalid dates
            }
        }
    }

    fun calculateTotalDividends(dividends: List<DividendDetail>): Double {
        return dividends.sumOf { it.totalDividend }
    }

    fun updateUsdPlnRateForDividends(stock: Stock): Stock {
        stock.dividends?.forEach { dividend ->
            val dayBefore = LocalDate.parse(dividend.paymentDate).minusDays(1)
            val usdPlnRate = getHistoricalExchangeRate(dayBefore)
            dividend.usdPlnRate = usdPlnRate
            dividend.withholdingTaxPaid = dividend.dividend * 0.15
            dividend.dividendInPln = dividend.dividend * usdPlnRate
            dividend.taxDueInPoland = dividend.dividendInPln * 0.19 - dividend.withholdingTaxPaid * usdPlnRate
        }
        return stock
    }

    fun getHistoricalExchangeRate(date: LocalDate): Double {
        val url = "https://api.exchangerate.host/${date}?base=USD&symbols=PLN"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()

        println("📦 Exchange rate response for $date: $responseBody")

        val root = objectMapper.readTree(responseBody)
        val rateNode = root.get("rates")?.get("PLN")

        if (rateNode == null || !rateNode.isNumber) {
            println("❌ Missing or invalid 'rates.PLN' for $date. Response: $responseBody")
            throw RuntimeException("Missing PLN exchange rate for $date")
        }

        return rateNode.asDouble()
    }

    fun calculateTaxToBePaidInPoland(stock: Stock): Stock {
        stock.taxToBePaidInPoland = stock.dividends?.sumOf { it.taxDueInPoland * it.quantity } ?: 0.0
        return stock
    }

    fun calculateTotalWithholdingTaxPaid(stock: Stock): Stock {
        stock.totalWithholdingTaxPaid = stock.dividends?.sumOf { it.withholdingTaxPaid * it.quantity } ?: 0.0
        return stock
    }
}