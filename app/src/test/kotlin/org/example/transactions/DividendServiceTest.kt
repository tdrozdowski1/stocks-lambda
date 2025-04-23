package org.example.transactions

import CurrentPriceData
import DividendDetail
import OwnershipPeriod
import Stock
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DividendServiceTest {

    private lateinit var dividendService: DividendService
    private lateinit var httpClient: HttpClient
    private lateinit var objectMapper: ObjectMapper

    // Mock Financial Data Service response for USD/PLN rates
    private val mockExchangeRateResponse = """
        {
          "historical": [
            {"date": "2023-12-01", "close": 4.5},
            {"date": "2023-11-30", "close": 4.4},
            {"date": "2023-11-01", "close": 4.2},
            {"date": "2023-10-31", "close": 4.1},
            {"date": "2022-10-30", "close": 4.0}
          ]
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        httpClient = mock(HttpClient::class.java)
        objectMapper = jacksonObjectMapper()
        dividendService = DividendService(httpClient, objectMapper)

        // Mock HTTP response for exchange rate requests
        val mockResponse = mock(HttpResponse::class.java) as HttpResponse<String>
        `when`(mockResponse.body()).thenReturn(mockExchangeRateResponse)
        `when`(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenReturn(mockResponse)
    }

    @Nested
    inner class FilterDividendsByOwnershipTests {
        @Test
        fun `should filter dividends based on ownership periods`() {
            // Given
            val dividends = listOf(
                DividendDetail(
                    date = "2024-01-10",
                    label = "Jan Dividend",
                    adjDividend = 1.5,
                    dividend = 1.5,
                    recordDate = "2024-01-09",
                    paymentDate = "2024-01-10",
                    declarationDate = "2024-01-01",
                    quantity = 1.0,
                    totalDividend = 1.5,
                    usdPlnRate = 0.0,
                    withholdingTaxPaid = 0.0,
                    dividendInPln = 0.0,
                    taxDueInPoland = 0.0
                ),
                DividendDetail(
                    date = "2024-02-10",
                    label = "Feb Dividend",
                    adjDividend = 1.0,
                    dividend = 1.0,
                    recordDate = "2024-02-09",
                    paymentDate = "2024-02-10",
                    declarationDate = "2024-02-01",
                    quantity = 1.0,
                    totalDividend = 2.0,
                    usdPlnRate = 0.0,
                    withholdingTaxPaid = 0.0,
                    dividendInPln = 0.0,
                    taxDueInPoland = 0.0
                ),
                DividendDetail(
                    date = "2024-03-10",
                    label = "Mar Dividend",
                    adjDividend = 2.0,
                    dividend = 2.0,
                    recordDate = "2024-03-09",
                    paymentDate = "2024-03-10",
                    declarationDate = "2024-03-01",
                    quantity = 1.0,
                    totalDividend = 2.0,
                    usdPlnRate = 0.0,
                    withholdingTaxPaid = 0.0,
                    dividendInPln = 0.0,
                    taxDueInPoland = 0.0
                )
            )
            val ownershipPeriods = listOf(
                OwnershipPeriod(startDate = "2024-01-01", endDate = "2024-01-15", quantity = 10.0),
                OwnershipPeriod(startDate = "2024-01-15", endDate = "2024-02-01", quantity = 5.0)
            )

            // When
            val filteredDividends = dividendService.filterDividendsByOwnership(dividends, ownershipPeriods)

            // Then
            assertEquals(1, filteredDividends.size)
            assertEquals("2024-01-10", filteredDividends[0].paymentDate)
            assertEquals(1.0, filteredDividends[0].quantity)
            assertEquals(1.5, filteredDividends[0].dividend)
            assertEquals(1.5, filteredDividends[0].totalDividend)
        }

        @Test
        fun `should return empty list if no relevant dividends`() {
            // Given
            val dividends = listOf(
                DividendDetail(
                    date = "2024-04-10",
                    label = "Apr Dividend",
                    adjDividend = 1.5,
                    dividend = 1.5,
                    recordDate = "2024-04-09",
                    paymentDate = "2024-04-10",
                    declarationDate = "2024-04-01",
                    quantity = 0.0,
                    totalDividend = 0.0,
                    usdPlnRate = 0.0,
                    withholdingTaxPaid = 0.0,
                    taxDueInPoland = 0.0,
                    dividendInPln = 0.0
                )
            )
            val ownershipPeriods = listOf(
                OwnershipPeriod(startDate = "2024-01-01", endDate = "2024-01-15", quantity = 10.0)
            )

            // When
            val filteredDividends = dividendService.filterDividendsByOwnership(dividends, ownershipPeriods)

            // Then
            assertEquals(0, filteredDividends.size)
        }
    }

    @Nested
    inner class CalculateTotalDividendsTests {
        @Test
        fun `should correctly calculate total dividends for multiple entries`() {
            // Given
            val relevantDividends = listOf(
                DividendDetail(
                    date = "2024-01-01",
                    label = "Jan Dividend",
                    adjDividend = 2.0,
                    dividend = 2.0,
                    recordDate = "2024-01-01",
                    paymentDate = "2024-01-01",
                    declarationDate = "2023-12-25",
                    quantity = 100.0,
                    totalDividend = 200.0,
                    usdPlnRate = 0.0,
                    withholdingTaxPaid = 0.0,
                    dividendInPln = 0.0,
                    taxDueInPoland = 0.0
                ),
                DividendDetail(
                    date = "2024-02-01",
                    label = "Feb Dividend",
                    adjDividend = 3.0,
                    dividend = 3.0,
                    recordDate = "2024-02-01",
                    paymentDate = "2024-02-01",
                    declarationDate = "2024-01-25",
                    quantity = 50.0,
                    totalDividend = 150.0,
                    usdPlnRate = 0.0,
                    withholdingTaxPaid = 0.0,
                    dividendInPln = 0.0,
                    taxDueInPoland = 0.0
                )
            )

            // When
            val result = dividendService.calculateTotalDividends(relevantDividends)

            // Then
            assertEquals(350.0, result, 0.001)
        }
    }

    @Nested
    inner class UpdateUsdPlnRateForDividendsTests {
        @Test
        fun `should update dividends with USD-PLN rate and calculated values`() {
            // Given
            val stock = Stock(
                symbol = "AAPL",
                moneyInvested = 1000.0,
                currentPrice = listOf(
                    CurrentPriceData(
                        symbol = "AAPL",
                        name = "Apple Inc.",
                        price = 150.0,
                        changesPercentage = 0.0,
                        change = 0.0,
                        dayLow = 0.0,
                        dayHigh = 0.0,
                        yearHigh = 0.0,
                        yearLow = 0.0,
                        marketCap = 0L,
                        priceAvg50 = 0.0,
                        priceAvg200 = 0.0,
                        exchange = "",
                        volume = 0L,
                        avgVolume = 0L,
                        open = 0.0,
                        previousClose = 0.0,
                        eps = 0.0,
                        pe = 0.0,
                        earningsAnnouncement = "",
                        sharesOutstanding = 0L,
                        timestamp = 0L
                    )
                ),
                ownershipPeriods = emptyList(),
                transactions = emptyList(),
                dividends = listOf(
                    DividendDetail(
                        date = "2023-12-01",
                        label = "Dec Dividend",
                        adjDividend = 100.0,
                        dividend = 100.0,
                        recordDate = "2023-11-30",
                        paymentDate = "2023-12-01",
                        declarationDate = "2023-11-25",
                        quantity = 10.0,
                        totalDividend = 0.0,
                        usdPlnRate = 0.0,
                        withholdingTaxPaid = 0.0,
                        dividendInPln = 0.0,
                        taxDueInPoland = 0.0
                    ),
                    DividendDetail(
                        date = "2023-11-01",
                        label = "Nov Dividend",
                        adjDividend = 200.0,
                        dividend = 200.0,
                        recordDate = "2023-10-31",
                        paymentDate = "2023-11-01",
                        declarationDate = "2023-10-25",
                        quantity = 5.0,
                        totalDividend = 0.0,
                        usdPlnRate = 0.0,
                        withholdingTaxPaid = 0.0,
                        dividendInPln = 0.0,
                        taxDueInPoland = 0.0
                    )
                ),
                totalDividendValue = 0.0,
                cashFlowData = null,
                liabilitiesData = null,
                totalWithholdingTaxPaid = null,
                taxToBePaidInPoland = null
            )

            // When
            val updatedStock = dividendService.updateUsdPlnRateForDividends(stock)

            // Then
            val dividends = updatedStock.dividends!!
            assertEquals(2, dividends.size)

            with(dividends[0]) { // 2023-12-01 (uses 2023-11-30 rate: 4.4)
                assertEquals("2023-12-01", paymentDate)
                assertEquals(4.4, usdPlnRate, 0.001)
                assertEquals(15.0, withholdingTaxPaid, 0.001)
                assertEquals(440.00000000000006, dividendInPln, 0.001)
                assertEquals(17.60000000000001, taxDueInPoland, 0.001)
            }

            with(dividends[1]) { // 2023-11-01 (uses 2023-10-31 rate: 4.1)
                assertEquals("2023-11-01", paymentDate)
                assertEquals(4.1, usdPlnRate, 0.001)
                assertEquals(30.0, withholdingTaxPaid, 0.001)
                assertEquals(819.9999999999999, dividendInPln, 0.001)
                assertEquals(32.8, taxDueInPoland, 0.001)
            }
        }

        @Test
        fun `should use rate from day earlier if no rate for payment day`() {
            // Given
            val stock = Stock(
                symbol = "AAPL",
                moneyInvested = 1000.0,
                currentPrice = listOf(
                    CurrentPriceData(
                        symbol = "AAPL",
                        name = "Apple Inc.",
                        price = 150.0,
                        changesPercentage = 0.0,
                        change = 0.0,
                        dayLow = 0.0,
                        dayHigh = 0.0,
                        yearHigh = 0.0,
                        yearLow = 0.0,
                        marketCap = 0L,
                        priceAvg50 = 0.0,
                        priceAvg200 = 0.0,
                        exchange = "",
                        volume = 0L,
                        avgVolume = 0L,
                        open = 0.0,
                        previousClose = 0.0,
                        eps = 0.0,
                        pe = 0.0,
                        earningsAnnouncement = "",
                        sharesOutstanding = 0L,
                        timestamp = 0L
                    )
                ),
                ownershipPeriods = emptyList(),
                transactions = emptyList(),
                dividends = listOf(
                    DividendDetail(
                        date = "2023-12-01",
                        label = "Dec Dividend",
                        adjDividend = 100.0,
                        dividend = 100.0,
                        recordDate = "2023-11-30",
                        paymentDate = "2023-12-01",
                        declarationDate = "2023-11-25",
                        quantity = 10.0,
                        totalDividend = 0.0,
                        usdPlnRate = 0.0,
                        withholdingTaxPaid = 0.0,
                        dividendInPln = 0.0,
                        taxDueInPoland = 0.0
                    )
                ),
                totalDividendValue = 0.0,
                cashFlowData = null,
                liabilitiesData = null,
                totalWithholdingTaxPaid = null,
                taxToBePaidInPoland = null
            )

            // When
            val updatedStock = dividendService.updateUsdPlnRateForDividends(stock)

            // Then
            val dividend = updatedStock.dividends!![0]
            assertEquals(4.4, dividend.usdPlnRate, 0.001) // Uses 2023-11-30 rate
            assertEquals(15.0, dividend.withholdingTaxPaid, 0.001)
            assertEquals(440.00000000000006, dividend.dividendInPln, 0.001)
            assertEquals(17.60000000000001, dividend.taxDueInPoland, 0.001)
        }

        @Test
        fun `should use rate from two days earlier if needed`() {
            // Given
            val stock = Stock(
                symbol = "AAPL",
                moneyInvested = 1000.0,
                currentPrice = listOf(
                    CurrentPriceData(
                        symbol = "AAPL",
                        name = "Apple Inc.",
                        price = 150.0,
                        changesPercentage = 0.0,
                        change = 0.0,
                        dayLow = 0.0,
                        dayHigh = 0.0,
                        yearHigh = 0.0,
                        yearLow = 0.0,
                        marketCap = 0L,
                        priceAvg50 = 0.0,
                        priceAvg200 = 0.0,
                        exchange = "",
                        volume = 0L,
                        avgVolume = 0L,
                        open = 0.0,
                        previousClose = 0.0,
                        eps = 0.0,
                        pe = 0.0,
                        earningsAnnouncement = "",
                        sharesOutstanding = 0L,
                        timestamp = 0L
                    )
                ),
                ownershipPeriods = emptyList(),
                transactions = emptyList(),
                dividends = listOf(
                    DividendDetail(
                        date = "2022-11-02",
                        label = "Nov Dividend",
                        adjDividend = 100.0,
                        dividend = 100.0,
                        recordDate = "2022-11-01",
                        paymentDate = "2022-11-02",
                        declarationDate = "2022-10-25",
                        quantity = 10.0,
                        totalDividend = 0.0,
                        usdPlnRate = 0.0,
                        withholdingTaxPaid = 0.0,
                        dividendInPln = 0.0,
                        taxDueInPoland = 0.0
                    )
                ),
                totalDividendValue = 0.0,
                cashFlowData = null,
                liabilitiesData = null,
                totalWithholdingTaxPaid = null,
                taxToBePaidInPoland = null
            )

            // When
            val updatedStock = dividendService.updateUsdPlnRateForDividends(stock)

            // Then
            val dividend = updatedStock.dividends!![0]
            assertEquals(4.0, dividend.usdPlnRate, 0.001) // Uses 2022-10-31 rate
            assertEquals(15.0, dividend.withholdingTaxPaid, 0.001)
            assertEquals(400.0, dividend.dividendInPln, 0.001)
            assertEquals(16.0, dividend.taxDueInPoland, 0.001)
        }
    }

    @Nested
    inner class CalculateTaxesTests {
        @Test
        fun `should calculate tax to be paid in Poland`() {
            // Given
            val stock = Stock(
                symbol = "AAPL",
                moneyInvested = 1000.0,
                currentPrice = listOf(
                    CurrentPriceData(
                        symbol = "AAPL",
                        name = "Apple Inc.",
                        price = 150.0,
                        changesPercentage = 0.0,
                        change = 0.0,
                        dayLow = 0.0,
                        dayHigh = 0.0,
                        yearHigh = 0.0,
                        yearLow = 0.0,
                        marketCap = 0L,
                        priceAvg50 = 0.0,
                        priceAvg200 = 0.0,
                        exchange = "",
                        volume = 0L,
                        avgVolume = 0L,
                        open = 0.0,
                        previousClose = 0.0,
                        eps = 0.0,
                        pe = 0.0,
                        earningsAnnouncement = "",
                        sharesOutstanding = 0L,
                        timestamp = 0L
                    )
                ),
                ownershipPeriods = emptyList(),
                transactions = emptyList(),
                dividends = listOf(
                    DividendDetail(
                        date = "2023-12-01",
                        label = "Dec Dividend",
                        adjDividend = 1.0,
                        dividend = 1.0,
                        recordDate = "2023-11-30",
                        paymentDate = "2023-12-01",
                        declarationDate = "2023-11-25",
                        quantity = 2.0,
                        totalDividend = 0.0,
                        usdPlnRate = 1.0,
                        withholdingTaxPaid = 1.0,
                        dividendInPln = 1.0,
                        taxDueInPoland = 10.0
                    ),
                    DividendDetail(
                        date = "2023-12-02",
                        label = "Dec Dividend 2",
                        adjDividend = 1.0,
                        dividend = 1.0,
                        recordDate = "2023-12-01",
                        paymentDate = "2023-12-02",
                        declarationDate = "2023-11-26",
                        quantity = 3.0,
                        totalDividend = 0.0,
                        usdPlnRate = 1.0,
                        withholdingTaxPaid = 1.0,
                        dividendInPln = 1.0,
                        taxDueInPoland = 5.0
                    )
                ),
                totalDividendValue = 0.0,
                cashFlowData = null,
                liabilitiesData = null,
                totalWithholdingTaxPaid = null,
                taxToBePaidInPoland = null
            )

            // When
            val updatedStock = dividendService.calculateTaxToBePaidInPoland(stock)

            // Then
            updatedStock.taxToBePaidInPoland?.let { assertEquals(35.0, it, 0.001) } // (10 * 2) + (5 * 3)
        }

        @Test
        fun `should calculate total withholding tax paid`() {
            // Given
            val stock = Stock(
                symbol = "AAPL",
                moneyInvested = 1000.0,
                currentPrice = listOf(
                    CurrentPriceData(
                        symbol = "AAPL",
                        name = "Apple Inc.",
                        price = 150.0,
                        changesPercentage = 0.0,
                        change = 0.0,
                        dayLow = 0.0,
                        dayHigh = 0.0,
                        yearHigh = 0.0,
                        yearLow = 0.0,
                        marketCap = 0L,
                        priceAvg50 = 0.0,
                        priceAvg200 = 0.0,
                        exchange = "",
                        volume = 0L,
                        avgVolume = 0L,
                        open = 0.0,
                        previousClose = 0.0,
                        eps = 0.0,
                        pe = 0.0,
                        earningsAnnouncement = "",
                        sharesOutstanding = 0L,
                        timestamp = 0L
                    )
                ),
                ownershipPeriods = emptyList(),
                transactions = emptyList(),
                dividends = listOf(
                    DividendDetail(
                        date = "2023-12-01",
                        label = "Dec Dividend",
                        adjDividend = 1.0,
                        dividend = 1.0,
                        recordDate = "2023-11-30",
                        paymentDate = "2023-12-01",
                        declarationDate = "2023-11-25",
                        quantity = 2.0,
                        totalDividend = 0.0,
                        usdPlnRate = 1.0,
                        withholdingTaxPaid = 1.5,
                        dividendInPln = 1.0,
                        taxDueInPoland = 1.0
                    ),
                    DividendDetail(
                        date = "2023-12-02",
                        label = "Dec Dividend 2",
                        adjDividend = 1.0,
                        dividend = 1.0,
                        recordDate = "2023-12-01",
                        paymentDate = "2023-12-02",
                        declarationDate = "2023-11-26",
                        quantity = 3.0,
                        totalDividend = 0.0,
                        usdPlnRate = 1.0,
                        withholdingTaxPaid = 2.0,
                        dividendInPln = 1.0,
                        taxDueInPoland = 1.0
                    )
                ),
                totalDividendValue = 0.0,
                cashFlowData = null,
                liabilitiesData = null,
                totalWithholdingTaxPaid = null,
                taxToBePaidInPoland = null
            )

            // When
            val updatedStock = dividendService.calculateTotalWithholdingTaxPaid(stock)

            // Then
            updatedStock.totalWithholdingTaxPaid?.let { assertEquals(9.0, it, 0.001) } // (1.5 * 2) + (2 * 3)
        }
    }
}

// Mockito helper to mock any HttpRequest
private inline fun <reified T> any(): T = org.mockito.Mockito.any(T::class.java)