package org.stocks.transactions.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.stocks.transactions.DividendDetail
import org.stocks.transactions.OwnershipPeriod
import java.math.BigDecimal
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DividendServiceTest {

    private lateinit var dividendService: DividendService
    private lateinit var httpClient: HttpClient
    private lateinit var objectMapper: ObjectMapper

    private val exchangeRates = mapOf(
        "2023-11-30" to BigDecimal("4.40"),
        "2023-10-31" to BigDecimal("4.10"),
        "2022-10-31" to BigDecimal("4.00"),
        "2022-11-01" to BigDecimal("4.05"),
        "2023-12-01" to BigDecimal("4.50"),
        "2023-11-01" to BigDecimal("4.20"),
        "2024-01-09" to BigDecimal("4.30")
    )

    @BeforeEach
    fun setUp() {
        httpClient = mock()
        objectMapper = jacksonObjectMapper()
        dividendService = DividendService(httpClient, objectMapper, apiKey = "test-api-key")

        whenever(
            httpClient.send(
                any<HttpRequest>(),
                eq(HttpResponse.BodyHandlers.ofString())
            )
        ).thenAnswer { invocation ->
            val request = invocation.getArgument<HttpRequest>(0)
            val uri = request.uri().toString()

            // Extract 'from' date from URL
            val dateRegex = Regex("""[?&]from=(\d{4}-\d{2}-\d{2})""")
            val match = dateRegex.find(uri)
            val date = match?.groupValues?.get(1)
                ?: throw RuntimeException("❌ No 'from' date found in URI: $uri")

            val rate = exchangeRates[date]
                ?: throw RuntimeException("❌ No exchange rate found for date $date")

            val responseBody = """
                {
                  "historical": [
                    { "date": "$date", "close": $rate }
                  ]
                }
            """.trimIndent()

            mock<HttpResponse<String>>().apply {
                whenever(body()).thenReturn(responseBody)
            }
        }
    }

    @Nested
    inner class ProcessDividendsTests {
        @Test
        fun `should filter dividends based on ownership periods and calculate taxes`() {
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.50"),
                    dividend = BigDecimal("1.50"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    currency = "USD",
                    quantity = BigDecimal.ZERO,
                    totalDividend = BigDecimal.ZERO,
                    usdPlnRate = BigDecimal.ZERO,
                    withholdingTaxPaid = BigDecimal.ZERO,
                    dividendInPln = BigDecimal.ZERO,
                    taxDueInPoland = BigDecimal.ZERO
                ),
                DividendDetail(
                    date = "2023-10-10",
                    label = "Oct Dividend",
                    adjDividend = BigDecimal("2.00"),
                    dividend = BigDecimal("2.00"),
                    recordDate = "2023-10-09",
                    paymentDate = "2023-10-31",
                    declarationDate = "2023-10-01",
                    currency = "USD",
                    quantity = BigDecimal.ZERO,
                    totalDividend = BigDecimal.ZERO,
                    usdPlnRate = BigDecimal.ZERO,
                    withholdingTaxPaid = BigDecimal.ZERO,
                    dividendInPln = BigDecimal.ZERO,
                    taxDueInPoland = BigDecimal.ZERO
                )
            )
            val ownershipPeriods = listOf(
                OwnershipPeriod(
                    startDate = "2024-01-01",
                    endDate = "2024-01-15",
                    quantity = BigDecimal("10.00")
                )
            )

            val processedDividends = dividendService.processDividends(dividends, ownershipPeriods)

            assertEquals(1, processedDividends.size)
            val dividend = processedDividends[0]
            assertEquals("2024-01-10", dividend.paymentDate)
            assertEquals(BigDecimal("10.00"), dividend.quantity)
            assertEquals(BigDecimal("1.50"), dividend.dividend)
            assertEquals(BigDecimal("15.00"), dividend.totalDividend)
            assertEquals(BigDecimal("4.30"), dividend.usdPlnRate)
            assertEquals(BigDecimal("0.23"), dividend.withholdingTaxPaid) // 1.50 * 0.15 = 0.225 -> 0.23
            assertEquals(BigDecimal("6.45"), dividend.dividendInPln) // 1.50 * 4.30 = 6.45
            assertEquals(BigDecimal("0.24"), dividend.taxDueInPoland) // (6.45 * 0.19) - (0.23 * 4.30) = 1.2255 - 0.989 = 0.2365 -> 0.24
        }

        @Test
        fun `should handle non-USD dividends with exchange rate conversion`() {
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.50"),
                    dividend = BigDecimal("1.50"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    currency = "EUR",
                    quantity = BigDecimal.ZERO,
                    totalDividend = BigDecimal.ZERO,
                    usdPlnRate = BigDecimal.ZERO,
                    withholdingTaxPaid = BigDecimal.ZERO,
                    dividendInPln = BigDecimal.ZERO,
                    taxDueInPoland = BigDecimal.ZERO
                )
            )
            val ownershipPeriods = listOf(
                OwnershipPeriod(
                    startDate = "2024-01-01",
                    endDate = "2024-01-15",
                    quantity = BigDecimal("10.00")
                )
            )

            // Mock EUR to USD rate
            whenever(
                httpClient.send(
                    any<HttpRequest>(),
                    eq(HttpResponse.BodyHandlers.ofString())
                )
            ).thenAnswer { invocation ->
                val request = invocation.getArgument<HttpRequest>(0)
                val uri = request.uri().toString()
                val responseBody = if (uri.contains("EURUSD")) {
                    """
                    {
                      "historical": [
                        { "date": "2024-01-09", "close": 1.10 }
                      ]
                    }
                    """.trimIndent()
                } else {
                    """
                    {
                      "historical": [
                        { "date": "2024-01-09", "close": 4.30 }
                      ]
                    }
                    """.trimIndent()
                }

                mock<HttpResponse<String>>().apply {
                    whenever(body()).thenReturn(responseBody)
                }
            }

            val processedDividends = dividendService.processDividends(dividends, ownershipPeriods)

            assertEquals(1, processedDividends.size)
            val dividend = processedDividends[0]
            assertEquals(BigDecimal("1.65"), dividend.dividend) // 1.50 * 1.10 = 1.65
            assertEquals(BigDecimal("16.50"), dividend.totalDividend) // 1.65 * 10 = 16.50
            assertEquals(BigDecimal("4.30"), dividend.usdPlnRate)
            assertEquals(BigDecimal("0.25"), dividend.withholdingTaxPaid) // 1.65 * 0.15 = 0.2475 -> 0.25
            assertEquals(BigDecimal("7.10"), dividend.dividendInPln) // 1.65 * 4.30 = 7.095 -> 7.10
            assertEquals(BigDecimal("0.27"), dividend.taxDueInPoland) // (7.10 * 0.19) - (0.25 * 4.30) = 1.349 - 1.075 = 0.274 -> 0.27
        }

        @Test
        fun `should handle invalid dates gracefully`() {
            val dividends = listOf(
                DividendDetail(
                    date = "invalid-date",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.50"),
                    dividend = BigDecimal("1.50"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    currency = "USD",
                    quantity = BigDecimal.ZERO,
                    totalDividend = BigDecimal.ZERO,
                    usdPlnRate = BigDecimal.ZERO,
                    withholdingTaxPaid = BigDecimal.ZERO,
                    dividendInPln = BigDecimal.ZERO,
                    taxDueInPoland = BigDecimal.ZERO
                )
            )
            val ownershipPeriods = listOf(
                OwnershipPeriod(
                    startDate = "2024-01-01",
                    endDate = "2024-01-15",
                    quantity = BigDecimal("10.00")
                )
            )

            val processedDividends = dividendService.processDividends(dividends, ownershipPeriods)

            assertEquals(0, processedDividends.size)
        }
    }

    @Nested
    inner class CalculationTests {
        @Test
        fun `should calculate total dividends correctly`() {
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.50"),
                    dividend = BigDecimal("1.50"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    currency = "USD",
                    quantity = BigDecimal("10.00"),
                    totalDividend = BigDecimal("15.00"),
                    usdPlnRate = BigDecimal("4.30"),
                    withholdingTaxPaid = BigDecimal("0.23"),
                    dividendInPln = BigDecimal("6.45"),
                    taxDueInPoland = BigDecimal("0.26")
                ),
                DividendDetail(
                    date = "2023-10-31",
                    label = "Oct Dividend",
                    adjDividend = BigDecimal("2.00"),
                    dividend = BigDecimal("2.00"),
                    recordDate = "2023-10-30",
                    paymentDate = "2023-10-31",
                    declarationDate = "2023-10-01",
                    currency = "USD",
                    quantity = BigDecimal("5.00"),
                    totalDividend = BigDecimal("10.00"),
                    usdPlnRate = BigDecimal("4.10"),
                    withholdingTaxPaid = BigDecimal("0.30"),
                    dividendInPln = BigDecimal("8.20"),
                    taxDueInPoland = BigDecimal("0.29")
                )
            )

            val totalDividends = dividendService.calculateTotalDividends(dividends)
            assertEquals(BigDecimal("25.00"), totalDividends) // 15.00 + 10.00 = 25.00
        }

        @Test
        fun `should calculate total withholding tax paid correctly`() {
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.50"),
                    dividend = BigDecimal("1.50"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    currency = "USD",
                    quantity = BigDecimal("10.00"),
                    totalDividend = BigDecimal("15.00"),
                    usdPlnRate = BigDecimal("4.30"),
                    withholdingTaxPaid = BigDecimal("0.23"),
                    dividendInPln = BigDecimal("6.45"),
                    taxDueInPoland = BigDecimal("0.26")
                ),
                DividendDetail(
                    date = "2023-10-31",
                    label = "Oct Dividend",
                    adjDividend = BigDecimal("2.00"),
                    dividend = BigDecimal("2.00"),
                    recordDate = "2023-10-30",
                    paymentDate = "2023-10-31",
                    declarationDate = "2023-10-01",
                    currency = "USD",
                    quantity = BigDecimal("5.00"),
                    totalDividend = BigDecimal("10.00"),
                    usdPlnRate = BigDecimal("4.10"),
                    withholdingTaxPaid = BigDecimal("0.30"),
                    dividendInPln = BigDecimal("8.20"),
                    taxDueInPoland = BigDecimal("0.29")
                )
            )

            val totalWithholdingTax = dividendService.calculateTotalWithholdingTaxPaid(dividends)
            assertEquals(BigDecimal("3.80"), totalWithholdingTax) // (0.23 * 10) + (0.30 * 5) = 2.30 + 1.50 = 3.80
        }

        @Test
        fun `should calculate total tax due in Poland correctly`() {
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.50"),
                    dividend = BigDecimal("1.50"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    currency = "USD",
                    quantity = BigDecimal("10.00"),
                    totalDividend = BigDecimal("15.00"),
                    usdPlnRate = BigDecimal("4.30"),
                    withholdingTaxPaid = BigDecimal("0.23"),
                    dividendInPln = BigDecimal("6.45"),
                    taxDueInPoland = BigDecimal("0.26")
                ),
                DividendDetail(
                    date = "2023-10-31",
                    label = "Oct Dividend",
                    adjDividend = BigDecimal("2.00"),
                    dividend = BigDecimal("2.00"),
                    recordDate = "2023-10-30",
                    paymentDate = "2023-10-31",
                    declarationDate = "2023-10-01",
                    currency = "USD",
                    quantity = BigDecimal("5.00"),
                    totalDividend = BigDecimal("10.00"),
                    usdPlnRate = BigDecimal("4.10"),
                    withholdingTaxPaid = BigDecimal("0.30"),
                    dividendInPln = BigDecimal("8.20"),
                    taxDueInPoland = BigDecimal("0.29")
                )
            )

            val totalTaxDue = dividendService.calculateTaxToBePaidInPoland(dividends)
            assertEquals(BigDecimal("4.05"), totalTaxDue) // (0.26 * 10) + (0.29 * 5) = 2.60 + 1.45 = 4.05
        }
    }
}