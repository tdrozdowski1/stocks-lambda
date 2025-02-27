package com.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

// Stock data class (unchanged, assuming it’s correct)
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

// Define data classes with at least one primary constructor parameter
data class CurrentPriceData(
    val symbol: String // Example field; add others as needed
)

data class OwnershipPeriod(
    val startDate: String // Example field; add others as needed
)

data class Transaction(
    val symbol: String // Example field; add others as needed
)

data class DividendDetail(
    val date: String // Example field; add others as needed
)

data class CashFlowData(
    val date: String // Example field; add others as needed
)

data class LiabilitiesData(
    val date: String // Example field; add others as needed
)

data class DeleteStockRequest(
    val symbol: String
)

class DeleteStockLambdaHandler : RequestHandler<Map<String, Any>, Map<String, Any>> {
    private val dynamoDbClient = DynamoDbClient.create()
    private val objectMapper = jacksonObjectMapper()

override fun handleRequest(event: Map<String, Any>, context: Context): Map<String, Any> {
    if (event["httpMethod"] == "OPTIONS") {
        return mapOf(
            "statusCode" to 200,
            "headers" to corsHeaders(),
            "body" to ""
        )
    }

    val pathParams = event["pathParameters"] as? Map<String, Any>
    val symbol = pathParams?.get("symbol") as? String
    if (symbol.isNullOrBlank()) {
        return errorResponse("Invalid request", "Symbol must be provided in path")
    }

    val deleteRequest = DeleteItemRequest.builder()
        .tableName("Stocks")
        .key(mapOf("symbol" to software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(symbol).build()))
        .build()

    dynamoDbClient.deleteItem(deleteRequest)
    return successResponse(mapOf("message" to "Stock deleted successfully", "symbol" to symbol))
}

    private fun successResponse(body: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "statusCode" to 200,
            "headers" to corsHeaders(),
            "body" to objectMapper.writeValueAsString(body)
        )
    }

    private fun errorResponse(errorMessage: String, details: String): Map<String, Any> {
        return mapOf(
            "statusCode" to 400,
            "headers" to corsHeaders(),
            "body" to objectMapper.writeValueAsString(mapOf("error" to errorMessage, "message" to details))
        )
    }

    private fun corsHeaders(): Map<String, String> {
        return mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-Amz-Date, Authorization, X-Api-Key, X-Amz-Security-Token"
        )
    }
}