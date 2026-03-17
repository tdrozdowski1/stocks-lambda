package org.stocks.transactions.services

import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.stocks.transactions.DividendDetail
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

/**
 * Alpha Vantage calls are throttled on free tier. Their responses may include:
 * - "Note": frequency limit hit
 * - "Information": guidance like "spread out ... (1 request per second)" and daily caps
 *
 * Official support states free usage "up to 25 requests per day" (limits can change). [1](https://www.alphavantage.co/support/)
 * Example throttle text includes "1 request per second". [2](https://www.alphavantage.co/query?function=OVERVIEW&symbol=IBM&apikey=YOUR_API_KEY)
 *
 * IMPORTANT: Even with this per-runtime limiter, Lambda concurrency can still exceed limits
 * if multiple instances share the same outbound IP (NAT). [3](https://stackoverflow.com/questions/79352540/alpha-vantage-daily-api-rate-limit-reached-shown-every-day)
 */
class FinancialModelingService(
        private val httpClient: HttpClient = HttpClient.newHttpClient(),
        private val objectMapper: ObjectMapper = jacksonObjectMapper(),

        private val apiKeyAlphaVantage: String = "1TDSFMP2GUKQQYTM",
        private val baseUrlAlphaVantage: String = "https://www.alphavantage.co/query"
) {

    /**
     * Shared rate limiter across the Lambda runtime (static memory per warm container).
     * Guarantees at most ~1 request / second within this runtime.
     */
    private object AlphaVantageRateLimiter {
        @Volatile private var nextAllowedAtMs: Long = 0L

        @Synchronized
        fun acquire(minIntervalMs: Long = 1100L) {
            val now = System.currentTimeMillis()
            val waitMs = nextAllowedAtMs - now
            if (waitMs > 0) Thread.sleep(waitMs)
            nextAllowedAtMs = System.currentTimeMillis() + minIntervalMs
        }
    }

    fun getStockPrice(symbol: String, context: Context): BigDecimal {
        context.logger.log("Fetching stock price (Alpha Vantage) for symbol: $symbol\n")

        val url = "$baseUrlAlphaVantage?function=GLOBAL_QUOTE&symbol=$symbol&apikey=$apiKeyAlphaVantage"
        val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

        val body = withAlphaVantageRetry(context, operation = "GLOBAL_QUOTE:$symbol") {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        }

        context.logger.log("Received response for stock price (Alpha Vantage): $body\n")
        val root = objectMapper.readTree(body)

        throwIfAlphaVantageError(root, symbol)

        val quote = root.get("Global Quote")
                ?: throw IllegalStateException("Missing 'Global Quote' in response for $symbol")

        val priceStr = quote.get("05. price")?.asText()
                ?: throw IllegalStateException("Missing '05. price' in Global Quote for $symbol")

        val price = try {
            BigDecimal(priceStr)
        } catch (e: Exception) {
            throw IllegalStateException("Cannot parse price '$priceStr' for $symbol", e)
        }

        context.logger.log("Successfully retrieved stock price for $symbol (Alpha Vantage): $price\n")
        return price
    }

    fun getDividends(
            symbol: String,
            from: String,
            to: String,
            context: Context
    ): List<DividendDetail> {

        context.logger.log("Fetching dividends (Alpha Vantage) for symbol: $symbol, range: $from..$to\n")

        // ✅ FIX: use apiKeyAlphaVantage (NOT apiKey used for FMP)
        val url = "$baseUrlAlphaVantage?function=DIVIDENDS&symbol=$symbol&apikey=$apiKeyAlphaVantage"
        val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

        val body = withAlphaVantageRetry(context, operation = "DIVIDENDS:$symbol") {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        }

        context.logger.log("Received response for dividends (Alpha Vantage): $body\n")
        val root = objectMapper.readTree(body)

        throwIfAlphaVantageError(root, symbol)

        // Keep your expected response structure:
        val dividendsNode = root.get("dividendsPerShare")
        if (dividendsNode == null || !dividendsNode.isArray) {
            context.logger.log("Unexpected Alpha Vantage response for $symbol (no dividendsPerShare array). Body: $body\n")
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
                                adjDividend = dividendAmount, // you assume AV already adjusted
                                recordDate = node.get("recordDate")?.asText() ?: "",
                                paymentDate = node.get("paymentDate")?.asText() ?: "",
                                declarationDate = node.get("declarationDate")?.asText() ?: ""
                        )
                )
            }
        }

        results.sortBy { it.date }
        context.logger.log("Successfully retrieved ${results.size} dividends for $symbol from Alpha Vantage\n")
        return results
    }

    /**
     * Executes [call] with:
     * - Global (per-runtime) 1 req/sec limiter
     * - Retry with exponential backoff on AV throttling responses (Note/Information)
     */
    private fun <T> withAlphaVantageRetry(
            context: Context,
            operation: String,
            maxAttempts: Int = 3,
            minIntervalMs: Long = 1100L,
            baseBackoffMs: Long = 1500L,
            call: () -> T
    ): T {
        var attempt = 1
        var backoff = baseBackoffMs

        while (true) {
            try {
                // Ensure at most ~1 request/sec in this warm Lambda container
                AlphaVantageRateLimiter.acquire(minIntervalMs)

                val result = call()

                // If result is a String body, we can proactively detect throttle text and throw to retry
                if (result is String) {
                    val root = objectMapper.readTree(result)
                    val throttleText = extractThrottleText(root)
                    if (throttleText != null) {
                        throw IllegalStateException("Alpha Vantage rate limit hit: $throttleText")
                    }
                }

                return result
            } catch (e: Exception) {
                val msg = e.message.orEmpty()

                val retryable =
                        msg.contains("rate limit", ignoreCase = true) ||
                                msg.contains("spreading out", ignoreCase = true) ||
                                msg.contains("Thank you for using Alpha Vantage", ignoreCase = true) ||
                                msg.contains("Note", ignoreCase = true) ||
                                msg.contains("Information", ignoreCase = true)

                if (!retryable || attempt >= maxAttempts) {
                    context.logger.log("Alpha Vantage call failed (no more retries). op=$operation attempt=$attempt msg=$msg\n")
                    throw e
                }

                context.logger.log(
                        "Alpha Vantage throttled/limited. Retrying with backoff. op=$operation attempt=$attempt/$maxAttempts backoffMs=$backoff msg=$msg\n"
                )
                Thread.sleep(backoff)
                backoff *= 2
                attempt++
            }
        }
    }

    private fun throwIfAlphaVantageError(root: JsonNode, symbol: String) {
        if (root.has("Error Message")) {
            throw IllegalStateException("Alpha Vantage error for $symbol: ${root.get("Error Message").asText()}")
        }
        if (root.has("Note")) {
            throw IllegalStateException("Alpha Vantage rate limit hit: ${root.get("Note").asText()}")
        }
        if (root.has("Information")) {
            throw IllegalStateException("Alpha Vantage information: ${root.get("Information").asText()}")
        }
    }

    private fun extractThrottleText(root: JsonNode): String? =
            when {
                root.has("Note") -> root.get("Note").asText()
                root.has("Information") -> root.get("Information").asText()
                else -> null
            }
}