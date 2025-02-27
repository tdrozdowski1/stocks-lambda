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
            context.logger.log("Processing DELETE request")

            // Extract the body from the event
            val body = event["body"] as? String
            if (body == null) {
                context.logger.log("Body is null")
                return errorResponse("Invalid request", "No body provided")
            }

            // Parse the request body to get the symbol with explicit type
            val request: DeleteStockRequest = try {
                objectMapper.readValue<DeleteStockRequest>(body)
            } catch (e: Exception) {
                context.logger.log("Failed to parse body: ${e.message}")
                return errorResponse("Invalid request", "Failed to parse request: ${e.message}")
            }

            val symbol = request.symbol
            if (symbol.isBlank()) {
                context.logger.log("Symbol is blank")
                return errorResponse("Invalid request", "Symbol cannot be empty")
            }

            // Delete the item from DynamoDB
            val deleteRequest = DeleteItemRequest.builder()
                .tableName("Stocks")
                .key(mapOf("symbol" to software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(symbol).build()))
                .build()

            context.logger.log("Attempting to delete stock with symbol: $symbol")
            dynamoDbClient.deleteItem(deleteRequest)
            context.logger.log("Successfully deleted stock with symbol: $symbol")

            successResponse(mapOf("message" to "Stock deleted successfully", "symbol" to symbol))
        } catch (e: Exception) {
            context.logger.log("Unexpected error: ${e.message}, stacktrace: ${e.stackTraceToString()}")
            errorResponse("Failed to delete stock", e.message ?: "Unknown error")
        }
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