package com.stocks

import Stock
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class SaveStockLambdaHandler(
    private val dynamoDbClient: DynamoDbClient = DynamoDbClient.create() // Default to real client
) : RequestHandler<Map<String, Any>, Map<String, Any>> {

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
                "ownershipPeriods" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.ownershipPeriods)).build(),
                "transactions" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.transactions)).build()
            )
            context.logger.log("Prepared DynamoDB item")

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