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

class DividendService(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val apiKey: String = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D",
    private val baseUrl: String = "https://financialmodelingprep.com/api/v3"
) {

    fun filterDividendsByOwnership(
        dividends: List<DividendDetail>,
        ownershipPeriods: List<OwnershipPeriod>
    ): List<DividendDetail> {
        return dividends.filter { dividend ->
            val dividendDate = LocalDate.parse(dividend.recordDate)
            ownershipPeriods.any { period ->
                val start = LocalDate.parse(period.startDate)
                val end = period.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
                dividendDate in start..end
            }
        }.map { dividend ->
            val period = ownershipPeriods.find { p ->
                val start = LocalDate.parse(p.startDate)
                val end = p.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
                LocalDate.parse(dividend.recordDate) in start..end
            }
            dividend.copy(
                quantity = period?.quantity ?: 0.0,
                totalDividend = dividend.dividend * (period?.quantity ?: 0.0)
            )
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

    private fun getHistoricalExchangeRate(date: LocalDate): Double {
        val formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/historical-price-full/USDPLN?from=$formattedDate&to=$formattedDate&apikey=$apiKey"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val json = objectMapper.readTree(response.body())
        return json["historical"][0]["close"].asDouble()
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