package com.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

// ✅ Define Stock Data Class
data class Stock(
    val symbol: String,
    val moneyInvested: Double,
    val currentPrice: Double,
    val ownershipPeriods: List<OwnershipPeriod>,
    val transactions: List<Transaction>,
    val dividends: List<DividendDetail>?,
    val totalDividendValue: Double,
    val cashFlowData: List<CashFlowData>?,
    val liabilitiesData: List<LiabilitiesData>?,
    val totalWithholdingTaxPaid: Double?,
    val taxToBePaidInPoland: Double?
)

data class OwnershipPeriod(val startDate: String, val endDate: String?, val quantity: Double)
data class Transaction(val symbol: String, val date: String, val type: String, val amount: Double, val price: Double, val commission: Double)
data class DividendDetail(val paymentDate: String, val dividend: Double, val quantity: Double, val dividendInPln: Double, val withholdingTaxPaid: Double, val taxDueInPoland: Double, val usdPlnRate: Double?)
data class CashFlowData(val date: String, val dividendsPaid: Double, val freeCashFlow: Double)
data class LiabilitiesData(val date: String, val totalLiabilities: Double, val totalAssets: Double, val totalEquity: Double, val totalDebt: Double)

class LambdaHandler : RequestHandler<Map<String, Any>, Map<String, Any>> {
    private val dynamoDbClient = DynamoDbClient.create()
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(event: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Received event: $event")

        // ✅ Handle CORS Preflight Request
        if (event["httpMethod"] == "OPTIONS") {
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        return try {
            // ✅ Parse Incoming Data
            val stock: Stock = objectMapper.convertValue(event, Stock::class.java)

            // ✅ Ensure Optional Fields Have Defaults
            val dividends = stock.dividends ?: emptyList()
            val cashFlowData = stock.cashFlowData ?: emptyList()
            val liabilitiesData = stock.liabilitiesData ?: emptyList()

            // ✅ Prepare Item for DynamoDB
            val item = mutableMapOf<String, AttributeValue>(
                "symbol" to AttributeValue.builder().s(stock.symbol).build(),
                "moneyInvested" to AttributeValue.builder().n(stock.moneyInvested.toString()).build(),
                "currentPrice" to AttributeValue.builder().n(stock.currentPrice.toString()).build(),
                "totalDividendValue" to AttributeValue.builder().n(stock.totalDividendValue.toString()).build(),
                "ownershipPeriods" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.ownershipPeriods)).build(),
                "transactions" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.transactions)).build(),
                "dividends" to AttributeValue.builder().s(objectMapper.writeValueAsString(dividends)).build(),
                "cashFlowData" to AttributeValue.builder().s(objectMapper.writeValueAsString(cashFlowData)).build(),
                "liabilitiesData" to AttributeValue.builder().s(objectMapper.writeValueAsString(liabilitiesData)).build()
            )

            stock.totalWithholdingTaxPaid?.let {
                item["totalWithholdingTaxPaid"] = AttributeValue.builder().n(it.toString()).build()
            } ?: run {
                item["totalWithholdingTaxPaid"] = AttributeValue.builder().nul(true).build()
            }

            stock.taxToBePaidInPoland?.let {
                item["taxToBePaidInPoland"] = AttributeValue.builder().n(it.toString()).build()
            } ?: run {
                item["taxToBePaidInPoland"] = AttributeValue.builder().nul(true).build()
            }

            // ✅ Save to DynamoDB
            val putRequest = PutItemRequest.builder()
                .tableName("Stocks")
                .item(item)
                .build()
            dynamoDbClient.putItem(putRequest)

            // ✅ Return Success Response with CORS Headers
            mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to objectMapper.writeValueAsString(mapOf("message" to "Stock saved successfully", "stock" to stock.symbol))
            )

        } catch (e: Exception) {
            context.logger.log("Error: ${e.message}")

            // ✅ Return Error Response with CORS Headers
            mapOf(
                "statusCode" to 500,
                "headers" to corsHeaders(),
                "body" to objectMapper.writeValueAsString(mapOf("error" to "Failed to save stock", "message" to (e.message ?: "Unknown error")))
            )
        }
    }

    // ✅ Function to Return CORS Headers
    private fun corsHeaders(): Map<String, String> {
        return mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-Amz-Date, Authorization, X-Api-Key, X-Amz-Security-Token"
        )
    }
}
