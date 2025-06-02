import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.stocks.db.DbService
import org.stocks.transactions.TransactionLambdaHandler
import org.stocks.transactions.services.DividendService
import org.stocks.transactions.services.FinancialCalculationsService
import org.stocks.transactions.services.FinancialModelingService
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class TransactionLambdaHandlerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var lambdaLogger: LambdaLogger

    @Mock
    private lateinit var financialModelingService: FinancialModelingService

    @Mock
    private lateinit var financialCalculationsService: FinancialCalculationsService

    @Mock
    private lateinit var dividendService: DividendService

    @Mock
    private lateinit var dbService: DbService

    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: TransactionLambdaHandler

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        // Configure context to return mock logger
        whenever(context.logger).thenReturn(lambdaLogger)
        // Allow logger.log to be called without throwing
        whenever(lambdaLogger.log(any<String>())).thenAnswer { /* No-op */ }
        handler = TransactionLambdaHandler(
            financialModelingService,
            financialCalculationsService,
            dividendService,
            dbService,
            objectMapper
        )
    }

    @Test
    fun `should handle valid POST request and return stock response`() {
        // Arrange
        val transaction = Transaction(
            symbol = "PEP",
            date = "2024-12-09",
            type = "buy",
            amount = BigDecimal("14"),
            price = BigDecimal("1"),
            commission = BigDecimal("1")
        )
        val input = mapOf(
            "httpMethod" to "POST",
            "body" to objectMapper.writeValueAsString(transaction)
        )

        // Mock dependencies
        val stock = Stock(
            symbol = "PEP",
            transactions = listOf(transaction),
            currentPrice = BigDecimal.ONE,
            moneyInvested = BigDecimal("15"), // price * amount + commission
            ownershipPeriods = emptyList(),
            dividends = emptyList(),
            totalDividendValue = BigDecimal.ZERO
        )
        whenever(dbService.getStocks()).thenReturn(emptyList())
        whenever(financialCalculationsService.calculateMoneyInvested(listOf(transaction))).thenReturn(BigDecimal("15"))
        whenever(financialCalculationsService.calculateOwnershipPeriods(listOf(transaction))).thenReturn(emptyList())
        whenever(financialModelingService.getStockPrice("PEP")).thenReturn(BigDecimal.ONE)
        whenever(financialModelingService.getDividends("PEP")).thenReturn(emptyList())
        whenever(dividendService.filterDividendsByOwnership(emptyList(), emptyList())).thenReturn(emptyList())
        whenever(dividendService.calculateTotalDividends(emptyList())).thenReturn(BigDecimal.ZERO)
        whenever(dividendService.updateUsdPlnRateForDividends(stock)).thenReturn(stock)
        whenever(dividendService.calculateTaxToBePaidInPoland(stock)).thenReturn(stock)
        whenever(dividendService.calculateTotalWithholdingTaxPaid(stock)).thenReturn(stock)

        // Act
        val response = handler.handleRequest(input, context)

        // Assert
        assertEquals(200, response["statusCode"])
        val headers = response["headers"] as Map<String, String>
        assertEquals("https://main.d1kexow7pbduqr.amplifyapp.com", headers["Access-Control-Allow-Origin"])
        assertEquals("OPTIONS,POST", headers["Access-Control-Allow-Methods"])
        assertEquals("Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token", headers["Access-Control-Allow-Headers"])

        val responseBody = response["body"] as String
        val returnedStock = objectMapper.readValue(responseBody, Stock::class.java)
        assertEquals("PEP", returnedStock.symbol)
        assertEquals(BigDecimal.ONE, returnedStock.currentPrice)
        assertEquals(1, returnedStock.transactions.size)
        assertEquals(transaction, returnedStock.transactions[0])
    }
}