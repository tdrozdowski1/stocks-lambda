package org.stocks.transactions

import Transaction
import Stock
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.stocks.db.DbService
import org.stocks.transactions.services.DividendService
import org.stocks.transactions.services.FinancialCalculationsService
import org.stocks.transactions.services.FinancialModelingService

class TransactionLambdaHandler(
    private val financialModelingService: FinancialModelingService = FinancialModelingService(),
    private val financialCalculationsService: FinancialCalculationsService = FinancialCalculationsService(),
    private val dividendService: DividendService = DividendService(),
    private val dbService: DbService = DbService(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : RequestHandler<Map<String, Any>, Map<String, Any>> {

    override fun handleRequest(input: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Raw input: $input\n")

        val method = input["httpMethod"] as? String ?: "GET"
        if (method == "OPTIONS") {
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        val transactionJson = input["body"] as? String
            ?: return buildCorsResponse(400, "Error: No transaction body provided")

        val transaction = try {
            objectMapper.readValue(transactionJson, Transaction::class.java)
        } catch (e: Exception) {
            context.logger.log("Deserialization error: ${e.message}")
            return buildCorsResponse(400, "Error: Invalid transaction format")
        }

        val stock = addTransaction(transaction)
        val responseBody = objectMapper.writeValueAsString(stock)

        return buildCorsResponse(200, responseBody)
    }

    private fun buildCorsResponse(statusCode: Int, body: String): Map<String, Any> {
        return mapOf(
            "statusCode" to statusCode,
            "headers" to corsHeaders(),
            "body" to body
        )
    }

    private fun addTransaction(transaction: Transaction): Stock {
        val currentStocks = dbService.getStocks()
        var stock = currentStocks.find { it.symbol == transaction.symbol }

        if (stock != null) {
            val updatedTransactions = stock.transactions + transaction
            stock = stock.copy(
                transactions = updatedTransactions,
                moneyInvested = financialCalculationsService.calculateMoneyInvested(updatedTransactions),
                ownershipPeriods = financialCalculationsService.calculateOwnershipPeriods(updatedTransactions)
            )
        } else {
            stock = Stock(
                symbol = transaction.symbol,
                transactions = listOf(transaction),
                moneyInvested = financialCalculationsService.calculateMoneyInvested(listOf(transaction)),
                ownershipPeriods = financialCalculationsService.calculateOwnershipPeriods(listOf(transaction))
            )
        }
        dbService.updateStock(stock)

        stock = stock.copy(currentPrice = financialModelingService.getStockPrice(stock.symbol))

        val dividendsData = financialModelingService.getDividends(stock.symbol)
        stock.dividends = dividendService.filterDividendsByOwnership(dividendsData, stock.ownershipPeriods)
        stock.totalDividendValue = dividendService.calculateTotalDividends(stock.dividends!!)

        stock = dividendService.updateUsdPlnRateForDividends(stock)
        stock = dividendService.calculateTaxToBePaidInPoland(stock)
        stock = dividendService.calculateTotalWithholdingTaxPaid(stock)

        return stock
    }

    private fun corsHeaders(): Map<String, String> {
        return mapOf(
            "Access-Control-Allow-Origin" to "https://main.d1kexow7pbduqr.amplifyapp.com",
            "Access-Control-Allow-Methods" to "OPTIONS,POST",
            "Access-Control-Allow-Headers" to "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token"
        )
    }
}
