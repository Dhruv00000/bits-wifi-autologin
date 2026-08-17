package com.example.bitsgoaauto_login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration.Companion.milliseconds

suspend fun sendLoginRequest(username: String, password: String): Result<String> = withContext(
    Dispatchers.IO
) {

    try {
        val probeConnection =
            URL("http://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
        probeConnection.connectTimeout = 3000
        probeConnection.readTimeout = 3000
        probeConnection.instanceFollowRedirects = false
        probeConnection.disconnect()

        if (probeConnection.responseCode == 204) {
            return@withContext Result.success("<message><![CDATA[Already authenticated]]></message>")
        }
    } catch (_: Exception) {
        // Probe failed or redirected, proceed with login request
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

            val connection =
                (URL("https://campnet.bits-goa.ac.in:8090/httpclient.html").openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Content-Length", postData.length.toString())
                }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                return@withContext Result.success(text)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP Error $responseCode"
                connection.disconnect()

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
