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
import java.time.LocalDate

class FinancialModelingService(
        private val httpClient: HttpClient = HttpClient.newHttpClient(),
        private val objectMapper: ObjectMapper = jacksonObjectMapper(),
        private val apiKey: String = "tQr6CjESc8UVhkFN4Eugr7WXpyYCu82D",
        private val baseUrlStable: String = "https://financialmodelingprep.com/stable",

        private val apiKeyAlphaVantage: String = "1TDSFMP2GUKQQYTM",
        private val baseUrlAlphaVantage: String = "https://www.alphavantage.co/query"
        ) {


    fun getStockPrice(symbol: String, context: Context): BigDecimal {
        context.logger.log("Fetching stock price (Alpha Vantage) for symbol: $symbol")

        val url = "$baseUrlAlphaVantage?function=GLOBAL_QUOTE&symbol=$symbol&apikey=$apiKeyAlphaVantage"
        val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            context.logger.log("Received response for stock price (Alpha Vantage): ${response.body()}")

            val root = objectMapper.readTree(response.body())

            if (root.has("Error Message")) {
                throw IllegalStateException("Alpha Vantage error for $symbol: ${root.get("Error Message").asText()}")
            }
            if (root.has("Note")) {
                throw IllegalStateException("Alpha Vantage rate limit hit: ${root.get("Note").asText()}")
            }
            if (root.has("Information")) {
                throw IllegalStateException("Alpha Vantage information: ${root.get("Information").asText()}")
            }

            val quote = root.get("Global Quote")
                    ?: throw IllegalStateException("Missing 'Global Quote' in response for $symbol")

            val priceStr = quote.get("05. price")?.asText()
                    ?: throw IllegalStateException("Missing '05. price' in Global Quote for $symbol")
            val price = try {
                BigDecimal(priceStr)
            } catch (e: Exception) {
                throw IllegalStateException("Cannot parse price '$priceStr' for $symbol", e)
            }

            context.logger.log("Successfully retrieved stock price for $symbol (Alpha Vantage): $price")
            return price

        } catch (e: Exception) {
            context.logger.log("Error fetching stock price (Alpha Vantage) for $symbol: ${e.message}")
            throw e
        }
    }

    fun getDividends(
            symbol: String,
            from: String,
            to: String,
            context: Context
    ): List<DividendDetail> {

        context.logger.log("Fetching dividends (Alpha Vantage) for symbol: $symbol, range: $from..$to")

        val url =
                "https://www.alphavantage.co/query" +
                        "?function=DIVIDENDS" +
                        "&symbol=$symbol" +
                        "&apikey=$apiKey"

        val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            context.logger.log("Received response for dividends (Alpha Vantage): ${response.body()}")

            val root = objectMapper.readTree(response.body())

            val dividendsNode = root.get("dividendsPerShare")
            if (dividendsNode == null || !dividendsNode.isArray) {
                context.logger.log("Unexpected Alpha Vantage response for $symbol: ${response.body()}")
                return emptyList()
            }

            val fromDate = LocalDate.parse(from)
            val toDate = LocalDate.parse(to)

            val results = mutableListOf<DividendDetail>()

            for (node in dividendsNode) {
                val dateStr = node.get("exDividendDate")?.asText() ?: continue
                val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
                if (d.isBefore(fromDate) || d.isAfter(toDate)) continue

                val dividendAmount = runCatching {
                    BigDecimal(node.get("amount")?.asText() ?: "0")
                }.getOrDefault(BigDecimal.ZERO)

                if (dividendAmount > BigDecimal.ZERO) {
                    results.add(
                            DividendDetail(
                                    date = dateStr,
                                    label = dateStr,
                                    dividend = dividendAmount,
                                    adjDividend = dividendAmount, // Alpha Vantage already adjusted
                                    recordDate = node.get("recordDate")?.asText() ?: "",
                                    paymentDate = node.get("paymentDate")?.asText() ?: "",
                                    declarationDate = node.get("declarationDate")?.asText() ?: ""
                            )
                    )
                }
            }

            results.sortBy { it.date }

            context.logger.log(
                    "Successfully retrieved ${results.size} dividends for $symbol from Alpha Vantage"
            )

            return results

        } catch (e: Exception) {
            context.logger.log("Error fetching dividends (Alpha Vantage) for $symbol: ${e.message}")
            throw e
        }
    }
}