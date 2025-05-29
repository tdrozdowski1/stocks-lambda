package com.stocks

import CashFlowData
import CurrentPriceData
import DividendDetail
import LiabilitiesData
import OwnershipPeriod
import Stock
import Transaction
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import java.math.BigDecimal

// Assuming same data classes as in your original code (Stock, CurrentPriceData, etc.)

class GetStocksLambda : RequestHandler<Map<String, Any>, Map<String, Any>> {
    private val dynamoDbClient = DynamoDbClient.create()
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(event: Map<String, Any>, context: Context): Map<String, Any> {
        context.logger.log("Received event: $event")

        // Handle CORS Preflight
        if (event["httpMethod"] == "OPTIONS") {
            return mapOf(
                "statusCode" to 200,
                "headers" to corsHeaders(),
                "body" to ""
            )
        }

        return try {
            context.logger.log("Processing GET request")

            // Scan the entire table (for all stocks)
            val scanRequest = ScanRequest.builder()
                .tableName("Stocks")
                .build()

            val scanResponse = dynamoDbClient.scan(scanRequest)
            val stocks = scanResponse.items().map { item ->
                Stock(
                    symbol = item["symbol"]?.s() ?: "",
                    moneyInvested = item["moneyInvested"]?.n()?.toBigDecimal() ?: BigDecimal.ZERO,
                    currentPrice = objectMapper.readValue(item["currentPrice"]?.s() ?: "[]", object : TypeReference<List<CurrentPriceData>>() {}),
                    ownershipPeriods = objectMapper.readValue(item["ownershipPeriods"]?.s() ?: "[]", object : TypeReference<List<OwnershipPeriod>>() {}),
                    transactions = objectMapper.readValue(item["transactions"]?.s() ?: "[]", object : TypeReference<List<Transaction>>() {}),
                    dividends = item["dividends"]?.s()?.let { objectMapper.readValue(it, object : TypeReference<List<DividendDetail>>() {}) },
                    totalDividendValue = item["totalDividendValue"]?.n()?.toBigDecimal() ?: BigDecimal.ZERO,
                    cashFlowData = item["cashFlowData"]?.s()?.let { objectMapper.readValue(it, object : TypeReference<List<CashFlowData>>() {}) },
                    liabilitiesData = item["liabilitiesData"]?.s()?.let { objectMapper.readValue(it, object : TypeReference<List<LiabilitiesData>>() {}) },
                    totalWithholdingTaxPaid = item["totalWithholdingTaxPaid"]?.n()?.toBigDecimal(),
                    taxToBePaidInPoland = item["taxToBePaidInPoland"]?.n()?.toBigDecimal()
                )
            }

            context.logger.log("Retrieved ${stocks.size} stocks")
            successResponse(stocks)
        } catch (e: Exception) {
            context.logger.log("Error retrieving stocks: ${e.message}")
            errorResponse("Failed to retrieve stocks", e.message ?: "Unknown error")
        }
    }

    private fun successResponse(stocks: List<Stock>): Map<String, Any> {
        return mapOf(
            "statusCode" to 200,
            "headers" to corsHeaders(),
            "body" to objectMapper.writeValueAsString(stocks)
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