package org.stocks.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.stocks.transactions.Stock
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

class DbService(
    private val dynamoDbClient: DynamoDbClient = DynamoDbClient.builder().build(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    private val tableName = "Stocks"

    fun getStocks(email: String): List<Stock> {
        val queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .indexName(email)
            .keyConditionExpression("email = :email")
            .expressionAttributeValues(mapOf(":email" to AttributeValue.builder().s(email).build()))
            .build()

        val response = dynamoDbClient.query(queryRequest)
        val items = response.items()

        return items.mapNotNull { item ->
            try {
                val stockJson = item["stockData"]?.s() ?: return@mapNotNull null
                val emailFromItem = item["email"]?.s() ?: "unknown@example.com"
                val stock = objectMapper.readValue<Stock>(stockJson)
                stock.copy(email = emailFromItem)
            } catch (e: Exception) {
                println("Error deserializing stock: ${e.message}")
                null
            }
        }
    }

    fun saveStock(stock: Stock) {
        val item = mutableMapOf<String, AttributeValue>(
            "symbol" to AttributeValue.builder().s(stock.symbol).build(),
            "moneyInvested" to AttributeValue.builder().n(stock.moneyInvested.toString()).build(),
            "ownershipPeriods" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.ownershipPeriods)).build(),
            "transactions" to AttributeValue.builder().s(objectMapper.writeValueAsString(stock.transactions)).build()
        )

        val putRequest = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()

        dynamoDbClient.putItem(putRequest)
    }
}