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
        private val baseUrlStable: String = "https://financialmodelingprep.com/stable",
        private val baseUrlV3: String = "https://financialmodelingprep.com/v3"
) {

    fun getStockPrice(symbol: String, context: Context): BigDecimal {
        context.logger.log("Fetching stock price for symbol: $symbol")

        try {
            val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrlStable/quote?symbol=$symbol&apikey=$apiKey"))
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            context.logger.log("Received response for stock price: ${response.body()}")

            val jsonArray = objectMapper.readTree(response.body())

            if (jsonArray.isEmpty) {
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


    fun getDividends(symbol: String, from: String, to: String, context: Context): List<DividendDetail> {
        context.logger.log("Fetching dividends (calendar) for symbol: $symbol, range: $from..$to")

        val url = "$baseUrlStable/dividends-calendar?from=$from&to=$to&apikey=$apiKey"
        val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            context.logger.log("Received response for dividends calendar: ${response.body()}")

            val json = objectMapper.readTree(response.body())
            if (!json.isArray) return emptyList()

            return json
                    .asSequence()
                    .filter { it["symbol"]?.asText() == symbol }
                    .map { node ->
                        DividendDetail(
                                date = node["date"]?.asText().orEmpty(),
                                label = node["date"]?.asText().orEmpty(),
                                adjDividend = node["adjDividend"]?.decimalValue() ?: java.math.BigDecimal.ZERO,
                                dividend = node["dividend"]?.decimalValue() ?: java.math.BigDecimal.ZERO,
                                recordDate = node["recordDate"]?.asText().orEmpty(),
                                paymentDate = node["paymentDate"]?.asText().orEmpty(),
                                declarationDate = node["declarationDate"]?.asText().orEmpty()
                        )
                    }
                    .toList()
        } catch (e: Exception) {
            context.logger.log("Error fetching dividends calendar for $symbol: ${e.message}")
            throw e
        }
    }
}