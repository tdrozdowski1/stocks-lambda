package org.example.transactions

import DividendDetail
import OwnershipPeriod
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.mockito.ArgumentMatchers.any
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class LambdaHandlerTest {

    private lateinit var dividendService: DividendService
    private lateinit var httpClient: HttpClient
    private lateinit var objectMapper: ObjectMapper

    private val exchangeRates = mapOf(
        "2023-11-30" to BigDecimal("4.4"),
        "2023-10-31" to BigDecimal("4.1"),
        "2022-10-31" to BigDecimal("4.0"),
        "2022-11-01" to BigDecimal("4.05"),
        "2023-12-01" to BigDecimal("4.5"),
        "2023-11-01" to BigDecimal("4.2")
    )

    @BeforeEach
    fun setUp() {
        httpClient = mock(HttpClient::class.java)
        objectMapper = jacksonObjectMapper()
        dividendService = DividendService(httpClient, objectMapper)

        `when`(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenAnswer { invocation ->
                val request = invocation.getArgument<HttpRequest>(0)
                val uri = request.uri().toString()
                val date = uri.substringAfter("from=").substringBefore("&")
                val rate = exchangeRates[date] ?: throw RuntimeException("No rate for $date")
                val responseBody = """
                    {
                      "historical": [
                        {"date": "$date", "close": $rate}
                      ]
                    }
                """.trimIndent()
                val mockResponse = mock(HttpResponse::class.java)
                @Suppress("UNCHECKED_CAST")
                `when`(mockResponse.body()).thenReturn(responseBody)
                mockResponse as HttpResponse<String>
            }
    }

    @Nested
    inner class FilterDividendsByOwnershipTests {
        @Test
        fun `should filter dividends based on ownership periods`() {
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = BigDecimal("1.5"),
                    dividend = BigDecimal("1.5"),
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    quantity = BigDecimal("1.0"),
                    totalDividend = BigDecimal("1.5"),
                    usdPlnRate = BigDecimal.ZERO,
                    withholdingTaxPaid = BigDecimal.ZERO,
                    dividendInPln = BigDecimal.ZERO,
                    taxDueInPoland = BigDecimal.ZERO
                )
            )
            val ownershipPeriods = listOf(
                OwnershipPeriod(startDate = "2024-01-01", endDate = "2024-01-15", quantity = BigDecimal("10.0"))
            )

            val filteredDividends = dividendService.filterDividendsByOwnership(dividends, ownershipPeriods)

            assertEquals(1, filteredDividends.size)
            assertEquals("2024-01-10", filteredDividends[0].paymentDate)
            assertEquals(BigDecimal("1.0"), filteredDividends[0].quantity)
            assertEquals(BigDecimal("1.5"), filteredDividends[0].dividend)
            assertEquals(BigDecimal("1.5"), filteredDividends[0].totalDividend)
        }
    }
}