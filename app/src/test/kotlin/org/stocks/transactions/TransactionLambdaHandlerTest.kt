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
        whenever(context.logger).thenReturn(lambdaLogger)
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
    fun `should handle valid POST request and return stock response with dividends`() {
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

        // Sample dividend and ownership period
        val dividend = DividendDetail(
            date = "2024-12-10",
            label = "Q4 Dividend",
            adjDividend = BigDecimal("0.5"),
            dividend = BigDecimal("0.5"),
            recordDate = "2024-12-09",
            paymentDate = "2024-12-10",
            declarationDate = "2024-12-01",
            quantity = BigDecimal.ZERO,
            totalDividend = BigDecimal.ZERO,
            usdPlnRate = BigDecimal.ZERO,
            withholdingTaxPaid = BigDecimal.ZERO,
            dividendInPln = BigDecimal.ZERO,
            taxDueInPoland = BigDecimal.ZERO
        )
        val ownershipPeriod = OwnershipPeriod(
            startDate = "2024-12-01",
            endDate = null,
            quantity = BigDecimal("14")
        )

        // Stock objects for different stages
        val initialStock = Stock(
            symbol = "PEP",
            transactions = listOf(transaction),
            currentPrice = BigDecimal.ONE,
            moneyInvested = BigDecimal("15"),
            ownershipPeriods = listOf(ownershipPeriod),
            dividends = emptyList(),
            totalDividendValue = null
        )
        val finalStock = Stock(
            symbol = "PEP",
            transactions = listOf(transaction),
            currentPrice = BigDecimal.ONE,
            moneyInvested = BigDecimal("15"),
            ownershipPeriods = listOf(ownershipPeriod),
            dividends = listOf(dividend.copy(quantity = BigDecimal("14"), totalDividend = BigDecimal("7.0"))),
            totalDividendValue = BigDecimal("7.0")
        )

        // Mock dependencies
        whenever(dbService.getStocks()).thenReturn(emptyList())
        whenever(financialCalculationsService.calculateMoneyInvested(listOf(transaction))).thenReturn(BigDecimal("15"))
        whenever(financialCalculationsService.calculateOwnershipPeriods(listOf(transaction))).thenReturn(listOf(ownershipPeriod))
        whenever(financialModelingService.getStockPrice("PEP")).thenReturn(BigDecimal.ONE)
        whenever(financialModelingService.getDividends("PEP")).thenReturn(listOf(dividend))
        whenever(dividendService.filterDividendsByOwnership(listOf(dividend), listOf(ownershipPeriod))).thenReturn(
            listOf(dividend.copy(quantity = BigDecimal("14"), totalDividend = BigDecimal("7.0")))
        )
        whenever(dividendService.calculateTotalDividends(any())).thenReturn(BigDecimal("7.0"))
        whenever(dividendService.updateUsdPlnRateForDividends(any())).thenReturn(finalStock)
        whenever(dividendService.calculateTaxToBePaidInPoland(any())).thenReturn(finalStock)
        whenever(dividendService.calculateTotalWithholdingTaxPaid(any())).thenReturn(finalStock)

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
        assertEquals(1, returnedStock.dividends?.size)
        assertEquals(BigDecimal("14"), returnedStock.dividends?.get(0)?.quantity)
        assertEquals(BigDecimal("7.0"), returnedStock.dividends?.get(0)?.totalDividend)
        assertEquals(BigDecimal("7.0"), returnedStock.totalDividendValue)
    }
}