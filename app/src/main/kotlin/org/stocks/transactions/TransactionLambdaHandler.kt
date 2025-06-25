package org.stocks.transactions

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.stocks.db.DbService
import org.stocks.transactions.services.*

class TransactionLambdaHandler(
    private val financialModelingService: FinancialModelingService = FinancialModelingService(),
    private val financialCalculationsService: FinancialCalculationsService = FinancialCalculationsService(),
    private val dividendService: DividendService = DividendService(),
    private val dbService: DbService = DbService(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : RequestHandler<Map<String, Any>, Map<String, Any>> {

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Raw input: $input\n")

        if (input["httpMethod"] == "OPTIONS") {
            return buildCorsResponse(200, "")
        }

        val transactionJson = input["body"] as? String
            ?: return buildCorsResponse(400, "No transaction body provided")

        return runCatching {
            val transaction = objectMapper.readValue(transactionJson, Transaction::class.java)
            val stock = processTransaction(transaction)
            buildCorsResponse(200, objectMapper.writeValueAsString(stock))
        }.getOrElse { e ->
            context.logger.log("Error: ${e.message}")
            buildCorsResponse(400, "Invalid transaction format: ${e.message}")
        }
    }

    private fun processTransaction(transaction: Transaction): Stock {
        val currentStocks = dbService.getStocks()
        val stock = currentStocks.find { it.symbol == transaction.symbol }?.let { existingStock: Stock ->
            updateExistingStock(existingStock, transaction)
        } ?: createNewStock(transaction)

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

    private fun createNewStock(transaction: Transaction): Stock {
        val transactions = listOf(transaction)
        return Stock(
            symbol = transaction.symbol,
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
        ).also { dbService.updateStock(it) }
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