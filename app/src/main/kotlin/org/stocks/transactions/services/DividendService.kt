package org.stocks.transactions.services

import DividendDetail
import OwnershipPeriod
import Stock
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DividendService(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val API_KEY: String = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D",
    private val BASE_URL: String = "https://financialmodelingprep.com/api/v3"
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

    fun calculateTotalDividends(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { it.totalDividend }
    }

    fun updateUsdPlnRateForDividends(stock: Stock): Stock {
        stock.dividends?.forEach { dividend ->
            val dayBefore = LocalDate.parse(dividend.paymentDate).minusDays(1)
            val usdPlnRate = getHistoricalExchangeRate(dayBefore)
            if (usdPlnRate != null) {
                dividend.usdPlnRate = usdPlnRate
            } else {
                println("⚠️ No USD/PLN exchange rate found for ${dayBefore}. Using default rate of 4.0")
                dividend.usdPlnRate = BigDecimal("4.0")
            }
            dividend.withholdingTaxPaid = dividend.dividend.multiply(BigDecimal("0.15"))
            dividend.dividendInPln = dividend.dividend.multiply(usdPlnRate)
            dividend.taxDueInPoland = dividend.dividendInPln.multiply(BigDecimal("0.19"))
                .subtract(dividend.withholdingTaxPaid.multiply(usdPlnRate))
        }
        return stock
    }

    fun getHistoricalExchangeRate(date: LocalDate): BigDecimal? {
        val startDate = date
        val endDate = date.minusDays(5)
        val url = "$BASE_URL/historical-price-full/forex/USDPLN?from=$endDate&to=$startDate&apikey=$API_KEY"

        println("📡 Fetching exchange rate for range $endDate to $startDate")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()

        println("📦 Exchange Rate API Response for $endDate to $startDate: $responseBody")

        val root = objectMapper.readTree(responseBody)
        val historical = root.get("historical")

        if (historical != null && historical.isArray && historical.size() > 0) {
            val rates = historical.map { node ->
                val rateDate = LocalDate.parse(node.get("date").asText())
                val closeRate = node.get("close")?.asDouble()
                Pair(rateDate, closeRate)
            }.filter { it.second != null }

            val sortedRates = rates.sortedWith(Comparator { a, b ->
                b.first.compareTo(a.first)
            })

            if (sortedRates.isNotEmpty()) {
                val (latestDate, latestRate) = sortedRates.first()
                val bigDecimalRate = latestRate!!.toBigDecimal()
                println("✅ Found USD/PLN rate for $latestDate: $bigDecimalRate")
                return bigDecimalRate
            }
        }

        println("⚠️ No USD/PLN exchange rate found for range $endDate to $startDate")
        return null // Return null if no rate found
    }

    fun calculateTaxToBePaidInPoland(stock: Stock): Stock {
        stock.taxToBePaidInPoland = stock.dividends?.sumOf { it.taxDueInPoland.multiply(it.quantity) } ?: BigDecimal.ZERO
        return stock
    }

    fun calculateTotalWithholdingTaxPaid(stock: Stock): Stock {
        stock.totalWithholdingTaxPaid = stock.dividends?.sumOf { it.withholdingTaxPaid.multiply(it.quantity) } ?: BigDecimal.ZERO
        return stock
    }
}
