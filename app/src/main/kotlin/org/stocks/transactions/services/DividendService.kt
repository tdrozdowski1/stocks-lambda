package org.stocks.transactions.services

import org.stocks.transactions.DividendDetail
import org.stocks.transactions.OwnershipPeriod
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
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

    private val USD_CURRENCY = "USD"
    private val PLN_CURRENCY = "PLN"
    private val USD_PLN_EXCHANGE_RATE_DEFAULT = BigDecimal("4.00")
    private val WITHOLDING_TAX_DEFAULT = BigDecimal("0.15")
    private val POLAND_TAX_DEFAULT = BigDecimal("0.19")
    private val SCALE = 2
    private val ROUNDING_MODE = RoundingMode.HALF_UP

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
            val dividendInUsd = if (dividend.currency != USD_CURRENCY) {
                val exchangeRateToUsd = getHistoricalExchangeRate(dividend.currency, USD_CURRENCY, paymentDate.minusDays(1))
                    ?: BigDecimal.ONE
                (dividend.dividend * exchangeRateToUsd).setScale(SCALE, ROUNDING_MODE)
            } else {
                dividend.dividend.setScale(SCALE, ROUNDING_MODE)
            }
            val totalDividend = (quantity * dividendInUsd).setScale(SCALE, ROUNDING_MODE)

            val usdPlnRate = getHistoricalExchangeRate(USD_CURRENCY, PLN_CURRENCY, paymentDate.minusDays(1))
                ?: USD_PLN_EXCHANGE_RATE_DEFAULT
            val withholdingTaxPaid = (dividendInUsd * WITHOLDING_TAX_DEFAULT).setScale(SCALE, ROUNDING_MODE)
            val dividendInPln = (dividendInUsd * usdPlnRate).setScale(SCALE, ROUNDING_MODE)
            val taxDueInPoland = (dividendInPln * POLAND_TAX_DEFAULT - withholdingTaxPaid * usdPlnRate)
                .setScale(SCALE, ROUNDING_MODE)

            dividend.copy(
                quantity = quantity,
                totalDividend = totalDividend,
                usdPlnRate = usdPlnRate.setScale(SCALE, ROUNDING_MODE),
                withholdingTaxPaid = withholdingTaxPaid,
                dividendInPln = dividendInPln,
                taxDueInPoland = taxDueInPoland,
                dividend = dividendInUsd
            )
        }
    }

    fun calculateTotalDividends(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { it.totalDividend ?: BigDecimal.ZERO }.setScale(SCALE, ROUNDING_MODE)
    }

    fun calculateTaxToBePaidInPoland(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { (it.taxDueInPoland ?: BigDecimal.ZERO) * (it.quantity ?: BigDecimal.ZERO) }
            .setScale(SCALE, ROUNDING_MODE)
    }

    fun calculateTotalWithholdingTaxPaid(dividends: List<DividendDetail>): BigDecimal {
        return dividends.sumOf { (it.withholdingTaxPaid ?: BigDecimal.ZERO) * (it.quantity ?: BigDecimal.ZERO) }
            .setScale(SCALE, ROUNDING_MODE)
    }

    private fun getHistoricalExchangeRate(fromCurrency: String, toCurrency: String, date: LocalDate): BigDecimal? {
        val currencyPair = "$fromCurrency$toCurrency"
        val url = "$baseUrl/historical-price-full/$currencyPair?from=$date&to=$date&apikey=$apiKey"
        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val root = objectMapper.readTree(response.body())
        val historical = root.get("historical")?.takeIf { it.isArray && it.size() > 0 }
            ?: return null

        val rate = historical[0].get("close")?.asDouble()?.toBigDecimal()
        return rate?.setScale(SCALE, ROUNDING_MODE)?.also { println("✅ Found $currencyPair rate for $date: $it") }
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