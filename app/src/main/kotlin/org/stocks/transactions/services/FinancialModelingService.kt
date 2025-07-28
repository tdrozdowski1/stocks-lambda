package org.stocks.transactions.services

import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.stocks.transactions.DividendDetail
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class FinancialModelingService(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val apiKey: String = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D",
    private val baseUrl: String = "https://financialmodelingprep.com/api/v3"
) {

    fun getStockPrice(symbol: String, context: Context): BigDecimal {
        context.logger.log("Fetching stock price for symbol: $symbol")
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/quote/$symbol?apikey=$apiKey"))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            context.logger.log("Received response for stock price: ${response.body()}")

            val jsonArray = objectMapper.readTree(response.body())
            if (jsonArray.isEmpty) {
                context.logger.log("Error: Empty response for stock price of $symbol")
                throw IllegalStateException("Empty response from API for symbol $symbol")
            }

            val price = jsonArray[0]["price"].decimalValue()
            context.logger.log("Successfully retrieved stock price for $symbol: $price")
            return price
        } catch (e: Exception) {
            context.logger.log("Error fetching stock price for $symbol: ${e.message}")
            throw e
        }
    }

    fun getDividends(symbol: String, context: Context): List<DividendDetail> {
        context.logger.log("Fetching dividends for symbol: $symbol")
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/historical-price-full/stock_dividend/$symbol?apikey=$apiKey"))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            context.logger.log("Received response for dividends: ${response.body()}")

            val json = objectMapper.readTree(response.body())
            val historical = json["historical"]
            if (historical == null || historical.isEmpty) {
                context.logger.log("No dividend history found for $symbol")
                return emptyList()
            }

            val dividends = historical.map {
                DividendDetail(
                    date = it["date"].asText(),
                    label = it["label"].asText(),
                    adjDividend = it["adjDividend"].decimalValue(),
                    dividend = it["dividend"].decimalValue(),
                    recordDate = it["recordDate"].asText(),
                    paymentDate = it["paymentDate"].asText(),
                    declarationDate = it["declarationDate"].asText()
                )
            }
            context.logger.log("Successfully retrieved ${dividends.size} dividends for $symbol")
            return dividends
        } catch (e: Exception) {
            context.logger.log("Error fetching dividends for $symbol: ${e.message}")
            throw e
        }
    }
}