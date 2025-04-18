package org.example.transactions

import DividendDetail
import Stock
import Transaction
import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class LambdaHandlerTest {

    private lateinit var transactionLambdaHandler: TransactionLambdaHandler
    private lateinit var httpClient: HttpClient
    private lateinit var dividendService: DividendService
    private lateinit var dbService: DbService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        httpClient = mock(HttpClient::class.java)
        dividendService = mock(DividendService::class.java)
        dbService = mock(DbService::class.java)
        objectMapper = jacksonObjectMapper()
        context = mock(Context::class.java)

        // Instantiate TransactionLambdaHandler with mocked dependencies
        transactionLambdaHandler = TransactionLambdaHandler(httpClient, dividendService, dbService, objectMapper)
    }

    @Test
    fun `should process a buy transaction and return stock JSON`() {
        // Given
        val transaction = Transaction(
            symbol = "AAPL",
            type = "buy",
            amount = 10.0,
            price = 150.0,
            commission = 5.0,
            date = "2025-03-27"
        )
        val input = mapOf("body" to objectMapper.writeValueAsString(transaction))

        // Mock DbService
        `when`(dbService.getStocks()).thenReturn(emptyList())

        // Mock HttpClient for stock price
        val stockPriceResponse = """
        [{
            "symbol": "AAPL",
            "name": "Apple Inc.",
            "price": 150.0,
            "changesPercentage": 0.0,
            "change": 0.0,
            "dayLow": 149.0,
            "dayHigh": 151.0,
            "yearHigh": 180.0,
            "yearLow": 120.0,
            "marketCap": 2400000000000,
            "priceAvg50": 145.0,
            "priceAvg200": 140.0,
            "exchange": "NASDAQ",
            "volume": 1000000,
            "avgVolume": 900000,
            "open": 149.5,
            "previousClose": 149.0,
            "eps": 6.0,
            "pe": 25.0,
            "earningsAnnouncement": "2025-04-30T00:00:00Z",
            "sharesOutstanding": 16000000000,
            "timestamp": 1648761600
        }]
    """.trimIndent()
        val stockPriceHttpResponse = mock(HttpResponse::class.java) as HttpResponse<String>
        `when`(stockPriceHttpResponse.body()).thenReturn(stockPriceResponse)

        // Mock HttpClient for dividends
        val dividendsResponse = """{"historical": []}"""
        val dividendsHttpResponse = mock(HttpResponse::class.java) as HttpResponse<String>
        `when`(dividendsHttpResponse.body()).thenReturn(dividendsResponse)

        `when`(httpClient.send(any(HttpRequest::class.java), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(stockPriceHttpResponse, dividendsHttpResponse)

        // Mock DividendService
        val emptyDividends = emptyList<DividendDetail>()
        `when`(dividendService.filterDividendsByOwnership(anyList(), anyList())).thenReturn(emptyDividends)
        `when`(dividendService.calculateTotalDividends(emptyDividends)).thenReturn(0.0)
        `when`(dividendService.updateUsdPlnRateForDividends(any())).thenAnswer { it.arguments[0] as Stock }
        `when`(dividendService.calculateTaxToBePaidInPoland(any())).thenAnswer { it.arguments[0] as Stock }
        `when`(dividendService.calculateTotalWithholdingTaxPaid(any())).thenAnswer { it.arguments[0] as Stock }

        // When
        val result = transactionLambdaHandler.handleRequest(input, context)

        // Then: Validate response structure
        val statusCode = result["statusCode"] as Int
        val headers = result["headers"] as Map<*, *>
        val body = result["body"] as String

        assertEquals(200, statusCode)
        assertEquals("https://main.d2nn1tu89v11eh.amplifyapp.com", headers["Access-Control-Allow-Origin"])

        val resultStock = objectMapper.readValue<Stock>(body)

        assertEquals("AAPL", resultStock.symbol)
        assertEquals(1505.0, resultStock.moneyInvested, 0.001)
        assertEquals(1, resultStock.transactions.size)
        assertEquals(10.0, resultStock.ownershipPeriods[0].quantity, 0.001)
        assertEquals(0.0, resultStock.totalDividendValue, 0.001)

        verify(dbService).updateStock(any())
    }
}

// Mockito helper to mock any type
private inline fun <reified T> any(): T = org.mockito.Mockito.any(T::class.java)