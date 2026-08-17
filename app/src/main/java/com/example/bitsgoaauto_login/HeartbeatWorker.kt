package com.example.bitsgoaauto_login

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val isPortalReachable = try {
                val connection =
                    URL("https://campnet.bits-goa.ac.in:8090/httpclient.html").openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                connection.disconnect()
                connection.responseCode != -1
            } catch (_: Exception) {
                false
            }
            if (!isPortalReachable) {
                val sharedPreferences = EncryptedSharedPreferences.create(
                    applicationContext,
                    "secret_shared_prefs",
                    MasterKey.Builder(applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                val username = sharedPreferences.getString("username", "") ?: ""
                val password = sharedPreferences.getString("password", "") ?: ""
                if (username.isNotBlank() && password.isNotBlank()) {
                    sendLoginRequest(username, password)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
