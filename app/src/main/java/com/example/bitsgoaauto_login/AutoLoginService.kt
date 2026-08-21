package com.example.bitsgoaauto_login

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class AutoLoginService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var sharedPreferences: EncryptedSharedPreferences
    private lateinit var settingsPreferences: SharedPreferences
    private var activeLoginJob: Job? = null
    private var currentNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            currentNetwork = network
        }

        override fun onLost(network: Network) {
            if (currentNetwork == network) {
                currentNetwork = null
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            currentNetwork = network
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                val currentSsid = getSsid(capabilities)

                if (MainViewModel.isCampusNetwork(currentSsid) || currentSsid == "<unknown ssid>" || currentSsid.isEmpty()) {
                    DebugLogger.log("Service detected portal. SSID: $currentSsid. Proceeding with auto-login check.")
                    attemptAutoLogin(network)
                } else {
                    DebugLogger.log("Service detected non-campus portal: $currentSsid. Ignoring.")
                }
            }
        }
    }

    private fun getSsid(capabilities: NetworkCapabilities): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: ""
            if (ssid == "<unknown ssid>" || ssid.isEmpty()) {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
            } else {
                ssid
            }
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        DebugLogger.log("AutoLoginService Created")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        startForegroundService()

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secret_shared_prefs",
            MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
        settingsPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(), networkCallback
        )

    }

    private fun startForegroundService() {

        val channelId = "auto_login_service_channel"
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(
                channelId,
                "Auto Login Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disableIntent = Intent(this, AutoLoginService::class.java).apply {
            action = ACTION_DISABLE_SERVICE
        }
        val disablePendingIntent = PendingIntent.getService(
            this, 1, disableIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val manualLoginIntent = Intent(this, AutoLoginService::class.java).apply {
            action = ACTION_MANUAL_LOGIN
        }
        val manualLoginPendingIntent = PendingIntent.getService(
            this, 2, manualLoginIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BITS Auto-Login Active")
            .setContentText("Monitoring Wi-Fi for login portal...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "Disable service", disablePendingIntent)
            .addAction(R.mipmap.ic_launcher, "Trigger login", manualLoginPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

    }

    private fun attemptAutoLogin(network: Network) {
        val username = sharedPreferences.getString("username", "") ?: ""
        val password = sharedPreferences.getString("password", "") ?: ""

        if (username.isBlank() || password.isBlank()) {
            DebugLogger.log("Auto-login skipped: Credentials missing")
            return
        }

        activeLoginJob?.cancel()
        activeLoginJob = serviceScope.launch {
            DebugLogger.log("Background login job started for network: $network")
            delay(2.seconds)

            val currentSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = connectivityManager.getNetworkCapabilities(network)?.transportInfo as? WifiInfo
                wifiInfo?.ssid?.replace("\"", "") ?: ""
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
            }.let {
                if (it == "<unknown ssid>" || it.isEmpty()) {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
                } else it
            }

            if (!MainViewModel.isCampusNetwork(currentSsid) && currentSsid != "<unknown ssid>" && currentSsid.isNotEmpty()) {
                DebugLogger.log("Re-checked SSID: $currentSsid. Definitely not campus. Aborting.")
                return@launch
            }

            var success = false
            var retries = 0
            val maxRetries = 3
            while (!success && retries < maxRetries) {
                DebugLogger.log("Background login attempt ${retries + 1}")
                val result = sendLoginRequest(username, password, network)
                result.onSuccess { response ->
                    DebugLogger.log("Background login success: $response")
                    if (response.contains("Login successful") || response.contains("Already authenticated") || response.contains(
                            "Already logged in"
                        )
                    ) {
                        success = true
                        connectivityManager.reportNetworkConnectivity(network, true)
                    }
                }.onFailure { error ->
                    DebugLogger.log("Background login fail (Attempt ${retries + 1}): ${error.message}")
                    retries++
                    if (retries < maxRetries) {
                        delay(3.seconds)
                    }
                }
                if (result.isSuccess) success = true
            }
            if (!success) DebugLogger.log("Background login failed after $maxRetries attempts")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE_SERVICE -> {
                DebugLogger.log("Service disabling from notification")
                settingsPreferences.edit { putBoolean("isServiceEnabled", false) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_MANUAL_LOGIN -> {
                DebugLogger.log("Manual login triggered from notification")
                currentNetwork?.let {
                    attemptAutoLogin(it)
                } ?: run {
                    DebugLogger.log("Manual login failed: No Wi-Fi network detected")
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.log("AutoLoginService Destroyed")
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    companion object {
        private const val ACTION_DISABLE_SERVICE = "com.example.bitsgoaauto_login.DISABLE_SERVICE"
        private const val ACTION_MANUAL_LOGIN = "com.example.bitsgoaauto_login.MANUAL_LOGIN"
    }

}
