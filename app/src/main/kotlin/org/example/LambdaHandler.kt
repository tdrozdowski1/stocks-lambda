package com.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class Stock(
    val symbol: String,
    val moneyInvested: Double,
    val currentPrice: List<CurrentPriceData>,
    val ownershipPeriods: List<OwnershipPeriod>,
    val transactions: List<Transaction>,
    val dividends: List<DividendDetail>?,
    val totalDividendValue: Double,
    val cashFlowData: List<CashFlowData>?,
    val liabilitiesData: List<LiabilitiesData>?,
    val totalWithholdingTaxPaid: Double?,
    val taxToBePaidInPoland: Double?
)

data class CurrentPriceData(
    val symbol: String,
    val name: String,
    val price: Double,
    val changesPercentage: Double,
    val change: Double,
    val dayLow: Double,
    val dayHigh: Double,
    val yearHigh: Double,
    val yearLow: Double,
    val marketCap: Long,
    val priceAvg50: Double,
    val priceAvg200: Double,
    val exchange: String,
    val volume: Long,
    val avgVolume: Long,
    val open: Double,
    val previousClose: Double,
    val eps: Double,
    val pe: Double,
    val earningsAnnouncement: String,
    val sharesOutstanding: Long,
    val timestamp: Long
)

data class OwnershipPeriod(val startDate: String, val endDate: String?, val quantity: Double)
data class Transaction(val symbol: String, val date: String, val type: String, val amount: Double, val price: Double, val commission: Double)
data class DividendDetail(
    val date: String,
    val label: String,
    val adjDividend: Double,
    val dividend: Double,
    val recordDate: String,
    val paymentDate: String,
    val declarationDate: String,
    val quantity: Double,
    val totalDividend: Double,
    val usdPlnRate: Double,
    val withholdingTaxPaid: Double,
    val dividendInPln: Double,
    val taxDueInPoland: Double
)
data class CashFlowData(val date: String, val dividendsPaid: Double, val freeCashFlow: Double)
data class LiabilitiesData(val date: String, val totalLiabilities: Double, val totalAssets: Double, val totalEquity: Double, val totalDebt: Double)

class LambdaHandler : RequestHandler<Map<String, Any>, Map<String, Any>> {
    private val dynamoDbClient = DynamoDbClient.create()
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(event: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Received event: $event")

        // Handle CORS Preflight Request
        if (event["httpMethod"] == "OPTIONS") {
            context.logger.log("Handling OPTIONS request")
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        return try {
            context.logger.log("Processing POST request")
            val body = event["body"] as? String
            if (body == null) {
                context.logger.log("Body is null")
                return errorResponse("Invalid request", "No body provided")
            }
            context.logger.log("Request body: $body")

            val stock: Stock = try {
                context.logger.log("Attempting to parse body")
                objectMapper.readValue(body)
            } catch (e: Exception) {
                context.logger.log("Failed to parse body: ${e.message}")
                return errorResponse("Invalid stock data", "Failed to parse stock: ${e.message}")
            }
            context.logger.log("Successfully parsed stock: ${stock.symbol}")

            val dividends = stock.dividends ?: emptyList()
            val cashFlowData = stock.cashFlowData ?: emptyList()
            val liabilitiesData = stock.liabilitiesData ?: emptyList()
            context.logger.log("Prepared optional fields: dividends size=${dividends.size}, cashFlowData size=${cashFlowData.size}, liabilitiesData size=${liabilitiesData.size}")

            val item = mutableMapOf<String, AttributeValue>(
                "symbol" to AttributeValue.builder().s(stock.symbol).build(),
                "moneyInvested" to AttributeValue.builder().n(stock.moneyInvested.toString()).build(),
                "currentPrice" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.currentPrice)).build(),
                "totalDividendValue" to AttributeValue.builder().n(stock.totalDividendValue.toString()).build(),
                "ownershipPeriods" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.ownershipPeriods)).build(),
                "transactions" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.transactions)).build(),
                "dividends" to AttributeValue.builder().s(objectMapper.writeValueAsString(dividends)).build(),
                "cashFlowData" to AttributeValue.builder().s(objectMapper.writeValueAsString(cashFlowData)).build(),
                "liabilitiesData" to AttributeValue.builder().s(objectMapper.writeValueAsString(liabilitiesData)).build()
            )
            context.logger.log("Prepared DynamoDB item")

            stock.totalWithholdingTaxPaid?.let {
                item["totalWithholdingTaxPaid"] = AttributeValue.builder().n(it.toString()).build()
            } ?: run { item["totalWithholdingTaxPaid"] = AttributeValue.builder().nul(true).build() }

            stock.taxToBePaidInPoland?.let {
                item["taxToBePaidInPoland"] = AttributeValue.builder().n(it.toString()).build()
            } ?: run { item["taxToBePaidInPoland"] = AttributeValue.builder().nul(true).build() }

            context.logger.log("Attempting to save to DynamoDB")
            val putRequest = PutItemRequest.builder()
                .tableName("Stocks")
                .item(item)
                .build()
            dynamoDbClient.putItem(putRequest)
            context.logger.log("Successfully saved to DynamoDB")

            successResponse(mapOf("message" to "Stock saved successfully", "stock" to stock.symbol))
        } catch (e: Exception) {
            context.logger.log("Unexpected error: ${e.message}, stacktrace: ${e.stackTraceToString()}")
            errorResponse("Failed to save stock", e.message ?: "Unknown error")
        }
    }

    private fun successResponse(body: Map<String, Any>): Map<String, Any> {
        val response = mapOf(
            "statusCode" to 200,
            "headers" to corsHeaders(),
            "body" to objectMapper.writeValueAsString(body)
        )
        return response
    }

    private fun errorResponse(errorMessage: String, details: String): Map<String, Any> {
        val response = mapOf(
            "statusCode" to 400,
            "headers" to corsHeaders(),
            "body" to objectMapper.writeValueAsString(mapOf("error" to errorMessage, "message" to details))
        )
        return response
    }

    private fun corsHeaders(): Map<String, String> {
        return mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-Amz-Date, Authorization, X-Api-Key, X-Amz-Security-Token"
        )
    }
}