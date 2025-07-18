package org.stocks.transactions.services

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

    fun getStockPrice(symbol: String): BigDecimal {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/quote/$symbol?apikey=$apiKey"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val jsonArray = objectMapper.readTree(response.body())
        return jsonArray[0]["price"].decimalValue()
    }

    fun getDividends(symbol: String): List<DividendDetail> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/historical-price-full/stock_dividend/$symbol?apikey=$apiKey"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val json = objectMapper.readTree(response.body())
        return json["historical"].map {
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
    }
}