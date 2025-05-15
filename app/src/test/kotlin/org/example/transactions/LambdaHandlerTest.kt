package com.example

import CurrentPriceData
import DividendDetail
import Stock
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse

class LambdaHandlerIntegrationTest {

    private lateinit var lambdaHandler: LambdaHandler
    private lateinit var dynamoDbClient: DynamoDbClient
    private lateinit var context: Context
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        // Mock DynamoDbClient
        dynamoDbClient = mock(DynamoDbClient::class.java)

        // Mock Context and Logger
        context = mock(Context::class.java)
        val logger = mock(LambdaLogger::class.java)
        `when`(context.logger).thenReturn(logger)

        // Inject mocked DynamoDbClient into LambdaHandler
        lambdaHandler = LambdaHandler(dynamoDbClient)
    }

    @Test
    fun `should handle valid stock request and save to DynamoDB`() {
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

        val event = mapOf(
            "httpMethod" to "POST",
            "body" to objectMapper.writeValueAsString(stock)
        )

        // Mock DynamoDB response
        `when`(dynamoDbClient.putItem(any(PutItemRequest::class.java))).thenReturn(PutItemResponse.builder().build())

        // When
        val response = lambdaHandler.handleRequest(event, context)

        // Then
        assertEquals(200, response["statusCode"])
        val responseBody = objectMapper.readValue<Map<String, Any>>(response["body"] as String)
        assertEquals("Stock saved successfully", responseBody["message"])
        assertEquals("AAPL", responseBody["stock"])

        // Verify DynamoDB interaction
        verify(dynamoDbClient).putItem(any(PutItemRequest::class.java))
    }

    @Test
    fun `should return error for invalid stock data`() {
        // Given
        val event = mapOf(
            "httpMethod" to "POST",
            "body" to "invalid-json"
        )

        // When
        val response = lambdaHandler.handleRequest(event, context)

        // Then
        assertEquals(400, response["statusCode"])
        val responseBody = objectMapper.readValue<Map<String, Any>>(response["body"] as String)
        assertEquals("Invalid stock data", responseBody["error"])
    }
}

// Mockito helper to mock any type
private inline fun <reified T> any(): T = org.mockito.Mockito.any(T::class.java)