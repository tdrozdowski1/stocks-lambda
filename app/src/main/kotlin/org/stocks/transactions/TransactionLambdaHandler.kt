package org.stocks.transactions

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.stocks.db.DbService
import org.stocks.transactions.services.*

@Suppress("UNCHECKED_CAST")
class TransactionLambdaHandler(
    private val financialModelingService: FinancialModelingService = FinancialModelingService(),
    private val financialCalculationsService: FinancialCalculationsService = FinancialCalculationsService(),
    private val dividendService: DividendService = DividendService(),
    private val dbService: DbService = DbService(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : RequestHandler<Map<String, Any>, Map<String, Any>> {

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Raw input: $input\n")
        context.logger.log("Input body: ${input["body"]}\n")
        context.logger.log("Input body type: ${input["body"]?.javaClass?.name}\n")

        if (input["httpMethod"] == "OPTIONS") {
            return buildCorsResponse(200, "")
        }

        val transactionJson = input["body"] as? String
            ?: return buildCorsResponse(400, "No transaction body provided")

        // Parse the transaction JSON directly
        return runCatching {
            val transaction = objectMapper.readValue<Transaction>(transactionJson)
            context.logger.log("Parsed transaction: $transaction\n")

            // Extract email
            val email = try {
                val requestContext = input["requestContext"] as? Map<*, *>
                val authorizer = requestContext?.get("authorizer") as? Map<*, *>
                val claims = authorizer?.get("claims") as? Map<*, *>
                claims?.get("email") as? String ?: "unknown@example.com"
            } catch (e: Exception) {
                context.logger.log("⚠️ Failed to extract email from claims: ${e.message}")
                "unknown@example.com"
            }

            context.logger.log("📩 Transaction for user: $email\n")
            val stock = processTransaction(transaction, email)
            buildCorsResponse(200, objectMapper.writeValueAsString(stock))
        }.getOrElse { e ->
            context.logger.log("Error parsing transaction: ${e.message}")
            buildCorsResponse(400, "Invalid transaction format: ${e.message}")
        }
    }

    private fun processTransaction(transaction: Transaction, email: String): Stock {
        val currentStocks = dbService.getStocks(email)
        val stock = currentStocks.find { it.symbol == transaction.symbol }?.let { existingStock: Stock ->
            updateExistingStock(existingStock, transaction)
        } ?: createNewStock(transaction, email)

        return enrichStockData(stock)
    }

    private fun updateExistingStock(stock: Stock, transaction: Transaction): Stock {
        val updatedTransactions = stock.transactions + transaction
        return stock.copy(
            transactions = updatedTransactions,
            moneyInvested = financialCalculationsService.calculateMoneyInvested(updatedTransactions),
            ownershipPeriods = financialCalculationsService.calculateOwnershipPeriods(updatedTransactions)
        )
    }

    private fun createNewStock(transaction: Transaction, email: String): Stock {
        val transactions = listOf(transaction)
        return Stock(
            symbol = transaction.symbol,
            email = email,
            transactions = transactions,
            moneyInvested = financialCalculationsService.calculateMoneyInvested(transactions),
            ownershipPeriods = financialCalculationsService.calculateOwnershipPeriods(transactions)
        )
    }

    private fun enrichStockData(stock: Stock): Stock {
        val updatedStock = stock.copy(currentPrice = financialModelingService.getStockPrice(stock.symbol))
        val dividendsData = financialModelingService.getDividends(stock.symbol)
        val processedDividends = dividendService.processDividends(dividendsData, stock.ownershipPeriods)

        return updatedStock.copy(
            dividends = processedDividends,
            totalDividendValue = dividendService.calculateTotalDividends(processedDividends),
            taxToBePaidInPoland = dividendService.calculateTaxToBePaidInPoland(processedDividends),
            totalWithholdingTaxPaid = dividendService.calculateTotalWithholdingTaxPaid(processedDividends)
        ).also { dbService.saveStock(it) }
    }

    private fun buildCorsResponse(statusCode: Int, body: String): Map<String, Any> {
        return mapOf(
            "statusCode" to statusCode,
            "headers" to corsHeaders(),
            "body" to body
        )
    }

    private fun corsHeaders(): Map<String, String> = mapOf(
        "Access-Control-Allow-Origin" to "https://main.d1kexow7pbduqr.amplifyapp.com",
        "Access-Control-Allow-Methods" to "OPTIONS,POST",
        "Access-Control-Allow-Headers" to "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token"
    )
}