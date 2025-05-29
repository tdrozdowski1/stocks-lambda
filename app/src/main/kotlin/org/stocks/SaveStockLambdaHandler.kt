package com.stocks

import Stock
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.stocks.db.DbService

class SaveStockLambdaHandler(
    private val dbService: DbService = DbService()
) : RequestHandler<Map<String, Any>, Map<String, Any>> {

    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(event: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Received event: $event")

        if (event["httpMethod"] == "OPTIONS") {
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        return try {
            val body = event["body"] as? String ?: return errorResponse("Invalid request", "No body provided")
            val stock: Stock = objectMapper.readValue(body)

            dbService.saveStock(stock)

            successResponse(mapOf("message" to "Stock saved successfully", "stock" to stock.symbol))
        } catch (e: Exception) {
            errorResponse("Failed to save stock", e.message ?: "Unknown error")
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