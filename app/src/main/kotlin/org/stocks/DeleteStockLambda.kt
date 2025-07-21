package com.stocks

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class DeleteStockLambdaHandler : RequestHandler<Map<String, Any>, Map<String, Any>> {
    private val dynamoDbClient = DynamoDbClient.create()
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(event: Map<String, Any>, context: Context): Map<String, Any> {
        val logger = context.logger
        logger.log("Received event: ${objectMapper.writeValueAsString(event)}\n")

        if (event["httpMethod"] == "OPTIONS") {
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        // Extract email from Cognito claims
        val email = try {
            val requestContext = event["requestContext"] as? Map<*, *>
            val authorizer = requestContext?.get("authorizer") as? Map<*, *>
            val claims = authorizer?.get("claims") as? Map<*, *>
            claims?.get("email") as? String
                ?: return errorResponse("Authentication error", "No email found in token")
        } catch (e: Exception) {
            logger.log("Failed to extract email: ${e.message}")
            return errorResponse("Authentication error", e.message ?: "Unknown error")
        }

        val pathParams = event["pathParameters"] as? Map<String, Any>
        val symbol = pathParams?.get("symbol") as? String
        if (symbol.isNullOrBlank()) {
            logger.log("Error: Symbol is null or blank\n")
            return errorResponse("Invalid request", "Symbol must be provided in path")
        }

        logger.log("Deleting stock with symbol: $symbol for email: $email\n")
        try {
            val deleteRequest = DeleteItemRequest.builder()
                .tableName("Stocks")
                .key(mapOf("symbol" to software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(symbol).build()))
                .conditionExpression("email = :email")
                .expressionAttributeValues(
                    mapOf(":email" to software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(email).build())
                )
                .build()

            dynamoDbClient.deleteItem(deleteRequest)
            logger.log("Stock deleted successfully for symbol: $symbol and email: $email\n")
            return successResponse(mapOf("message" to "Stock deleted successfully", "symbol" to symbol))
        } catch (e: Exception) {
            logger.log("Error deleting stock: ${e.message}\n")
            return errorResponse(
                "Failed to delete stock",
                if (e.message?.contains("ConditionalCheckFailed") == true)
                    "Stock not found or not owned by user"
                else
                    e.message ?: "Unknown error"
            )
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
            "Access-Control-Allow-Origin" to "https://main.d1kexow7pbduqr.amplifyapp.com",
            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-Amz-Date, Authorization, X-Api-Key, X-Amz-Security-Token"
        )
    }
}