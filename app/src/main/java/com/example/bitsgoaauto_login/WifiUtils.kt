package com.example.bitsgoaauto_login

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration.Companion.milliseconds

suspend fun sendLoginRequest(
    username: String,
    password: String,
    network: Network? = null
): Result<String> = withContext(Dispatchers.IO) {
    DebugLogger.log("sendLoginRequest started. Network=$network")

    try {
        val probeUrl = URL("http://connectivitycheck.gstatic.com/generate_204")
        DebugLogger.log("Checking internet probe: $probeUrl")
        val probeConnection = if (network != null) {
            network.openConnection(probeUrl)
        } else {
            probeUrl.openConnection()
        } as HttpURLConnection

        probeConnection.connectTimeout = 3000
        probeConnection.readTimeout = 3000
        probeConnection.instanceFollowRedirects = false
        probeConnection.disconnect()

        if (probeConnection.responseCode == 204) {
            DebugLogger.log("Internet probe success (204). Device already authenticated.")
            return@withContext Result.success("<message><![CDATA[Already authenticated]]></message>")
        } else {
            DebugLogger.log("Internet probe redirected/failed (Code: ${probeConnection.responseCode}). Proceeding to login.")
        }
    } catch (e: Exception) {
        DebugLogger.log("Internet probe exception: ${e.message}. Proceeding to login.")
    }

    var lastThrowable: Throwable? = null
    val maxRetries = 3

    for (attempt in 1..maxRetries) {
        try {
            val postData = "mode=191&username=${URLEncoder.encode(username, "UTF-8")}&password=${
                URLEncoder.encode(
                    password,
                    "UTF-8"
                )
            }&a=${System.currentTimeMillis()}&producttype=0"

            val portalUrl = URL("https://campnet.bits-goa.ac.in:8090/httpclient.html")
            val connection = if (network != null) {
                network.openConnection(portalUrl)
            } else {
                portalUrl.openConnection()
            } as HttpsURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Content-Length", postData.length.toString())
            }

            DebugLogger.log("Sending portal request: ${connection.url}")
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            DebugLogger.log("Portal response code: $responseCode")
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                DebugLogger.log("Portal response content: ${text.take(100)}...")
                return@withContext Result.success(text)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP Error $responseCode"
                connection.disconnect()
                DebugLogger.log("Portal login failed: $errorText")

                val exception = Exception("Server returned status code: $errorText")

                if (responseCode < 500) {
                    return@withContext Result.failure(exception)
                }
                lastThrowable = exception
            }
        } catch (e: Exception) {
            lastThrowable = e
        }

        if (attempt < maxRetries) {

            delay((attempt * 2000).milliseconds)
        }
    }

    Result.failure(lastThrowable ?: Exception("Login failed after $maxRetries attempts"))
}
