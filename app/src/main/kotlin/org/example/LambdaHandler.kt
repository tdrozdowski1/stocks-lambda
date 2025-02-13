package com.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.core.type.TypeReference

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
        return try {
            // ✅ Log event for debugging
            context.logger.log("Received event: $event")

            // ✅ FIX: Explicitly specify the type
            val stock: Stock = objectMapper.convertValue(event, Stock::class.java)

            val dividends: List<DividendDetail> = stock.dividends ?: emptyList()
            val cashFlowData: List<CashFlowData> = stock.cashFlowData ?: emptyList()
            val liabilitiesData: List<LiabilitiesData> = stock.liabilitiesData ?: emptyList()

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

            val putRequest = PutItemRequest.builder()
                .tableName("Stocks")
                .item(item)
                .build()

            dynamoDbClient.putItem(putRequest)

            mapOf("message" to "Stock saved successfully", "stock" to stock.symbol)
        } catch (e: Exception) {
            context.logger.log("Error: ${e.message}")
            mapOf("error" to "Failed to save stock", "message" to (e.message ?: "Unknown error"))
        }
    }
}
