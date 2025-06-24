package org.stocks.transactions.services

import DividendDetail
import OwnershipPeriod
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DividendService(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val apiKey: String = System.getenv("FINANCIAL_MODELING_API_KEY") ?: "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D",
    private val baseUrl: String = System.getenv("FINANCIAL_MODELING_BASE_URL") ?: "https://financialmodelingprep.com/api/v3"
) {

    fun processDividends(dividends: List<DividendDetail>, ownershipPeriods: List<OwnershipPeriod>): List<DividendDetail> {
        return dividends.filter { dividend ->
            safeParseDate(dividend.date)?.let { dividendDate ->
                ownershipPeriods.any { period ->
                    val start = safeParseDate(period.startDate) ?: return@any false
                    val end = period.endDate?.let { safeParseDate(it) }
                    dividendDate >= start && (end == null || dividendDate <= end)
                }
            } ?: false
        }.map { dividend ->
            val paymentDate = safeParseDate(dividend.paymentDate) ?: return@map dividend
            val matchingPeriod = ownershipPeriods.find { period ->
                val start = safeParseDate(period.startDate) ?: return@find false
                val end = period.endDate?.let { safeParseDate(it) }
                paymentDate >= start && (end == null || paymentDate < end)
            }

            val quantity = matchingPeriod?.quantity ?: BigDecimal.ZERO
            val totalDividend = quantity * dividend.dividend
            val usdPlnRate = getHistoricalExchangeRate(paymentDate.minusDays(1)) ?: BigDecimal("4.0")
            val withholdingTaxPaid = dividend.dividend * BigDecimal("0.15")
            val dividendInPln = dividend.dividend * usdPlnRate
            val taxDueInPoland = dividendInPln * BigDecimal("0.19") - withholdingTaxPaid * usdPlnRate

            dividend.copy(
                quantity = quantity,
                totalDividend = totalDividend,
                usdPlnRate = usdPlnRate,
                withholdingTaxPaid = withholdingTaxPaid,
                dividendInPln = dividendInPln,
                taxDueInPoland = taxDueInPoland
            )
        }
    }

    fun calculateTotalDividends(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { it.totalDividend }
    }

    fun calculateTaxToBePaidInPoland(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { it.taxDueInPoland * it.quantity }
    }

    fun calculateTotalWithholdingTaxPaid(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { it.withholdingTaxPaid * it.quantity }
    }

    private fun getHistoricalExchangeRate(date: LocalDate): BigDecimal? {
        val url = "$baseUrl/historical-price-full/USDPLN?from=$date&to=$date&apikey=$apiKey"
        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val root = objectMapper.readTree(response.body())
        val historical = root.get("historical")?.takeIf { it.isArray && it.size() > 0 }
            ?: return null

        val rate = historical[0].get("close")?.asDouble()?.toBigDecimal()
        return rate?.also { println("✅ Found USD/PLN rate for $date: $it") }
    }

    private fun safeParseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            println("⚠️ Invalid date format: $dateStr")
            null
        }
    }
}