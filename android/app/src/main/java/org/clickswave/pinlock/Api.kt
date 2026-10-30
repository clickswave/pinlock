package org.clickswave.pinlock

import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

data class User(val name: String, val email: String, val plan: String)

data class Order(
    val id: String,
    val item: String,
    val total: Double,
    val currency: String,
    val status: String,
    val placed: String,
    val card: String,
)

/** Why a request failed, worded for someone watching the screen. */
class Refusal(val headline: String, val detail: String, val raw: String) : Exception(detail)

class Api(private val host: String = BuildConfig.API_HOST) {
    private val client = OkHttpClient.Builder()
        .certificatePinner(Pins.pinner(host))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun config(): JSONObject = call(Request.Builder().url(url("/v1/config")).build())

    suspend fun login(email: String, password: String): Pair<String, User> {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val res = call(Request.Builder().url(url("/v1/login")).post(body.toRequestBody(jsonType)).build())
        return res.getString("token") to user(res.getJSONObject("user"))
    }

    suspend fun me(token: String): User = user(call(authed("/v1/me", token)))

    suspend fun orders(token: String): List<Order> {
        val list = call(authed("/v1/orders", token)).getJSONArray("orders")
        return (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            Order(
                id = o.getString("id"),
                item = o.getString("item"),
                total = o.getDouble("total"),
                currency = o.getString("currency"),
                status = o.getString("status"),
                placed = o.getString("placed"),
                card = o.getString("card"),
            )
        }
    }

    private fun url(path: String) = "https://$host$path"

    private fun authed(path: String, token: String) =
        Request.Builder().url(url(path)).header("Authorization", "Bearer $token").build()

    private fun user(o: JSONObject) = User(o.getString("name"), o.getString("email"), o.getString("plan"))

    private suspend fun call(request: Request): JSONObject = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val code = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                    throw Refusal(
                        "The server said no",
                        if (code == "invalid_credentials") "Wrong email or password." else "HTTP ${res.code}",
                        "HTTP ${res.code} ${code.ifEmpty { text.take(120) }}",
                    )
                }
                JSONObject(text)
            }
        } catch (e: Refusal) {
            throw e
        } catch (e: JSONException) {
            throw Refusal("Unexpected response", "The server didn't answer with JSON.", e.message.orEmpty().take(300))
        } catch (e: IOException) {
            throw explain(e)
        }
    }

    private fun explain(e: IOException): Refusal {
        val raw = "${e.javaClass.simpleName}: ${e.message.orEmpty()}".take(300)
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        val messages = chain.joinToString(" ") { it.message.orEmpty() }
        return when {
            // OkHttp's own pinner, layer two.
            e is SSLPeerUnverifiedException && messages.contains("Certificate pinning failure") ->
                Refusal("Blocked by certificate pinning", "The server's certificate chain doesn't contain a pinned key.", raw)
            // Android's network security config, layer one.
            messages.contains("Pin verification failed") ->
                Refusal("Blocked by certificate pinning", "Android refused the chain: no pinned key in it.", raw)
            e is SSLHandshakeException && chain.any { it is CertificateException } ->
                Refusal("Blocked: certificate not trusted", "Whoever answered doesn't hold a certificate this app trusts.", raw)
            e is SSLHandshakeException ->
                Refusal("TLS handshake failed", "The secure connection couldn't be set up.", raw)
            e is UnknownHostException ->
                Refusal("No connection", "Couldn't reach $host.", raw)
            else -> Refusal("Request failed", e.message ?: "Network error.", raw)
        }
    }
}
