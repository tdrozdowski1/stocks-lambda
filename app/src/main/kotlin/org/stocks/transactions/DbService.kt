package org.stocks.transactions

import Stock
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

class DbService(
    private val dynamoDbClient: DynamoDbClient = DynamoDbClient.builder().build(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    private val tableName = "Stocks"

    fun getStocks(): List<Stock> {
        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(mapOf("symbol" to AttributeValue.builder().s("ALL").build()))
            .build()
        val response = dynamoDbClient.getItem(request)
        val stocksJson = response.item()?.get("stocks")?.s() ?: "[]"
        return objectMapper.readValue(stocksJson)
    }

    fun updateStock(stock: Stock) {
        val currentStocks = getStocks().toMutableList()
        currentStocks.removeIf { it.symbol == stock.symbol }
        currentStocks.add(stock)
        val item = mapOf(
            "symbol" to AttributeValue.builder().s("ALL").build(),
            "stocks" to AttributeValue.builder().s(objectMapper.writeValueAsString(currentStocks)).build()
        )
        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()
        dynamoDbClient.putItem(request)
    }
}