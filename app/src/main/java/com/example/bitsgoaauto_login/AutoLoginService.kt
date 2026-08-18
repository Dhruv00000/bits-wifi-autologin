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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoLoginService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var sharedPreferences: EncryptedSharedPreferences
    private lateinit var settingsPreferences: SharedPreferences

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                val currentSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val wifiInfo = capabilities.transportInfo as? WifiInfo
                    wifiInfo?.ssid?.replace("\"", "") ?: ""
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
                }

                if (MainViewModel.isCampusNetwork(currentSsid)) {
                    attemptAutoLogin()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

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

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BITS Auto-Login Active")
            .setContentText("Monitoring Wi-Fi for login portal...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "Disable background service", disablePendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

    }

    private fun attemptAutoLogin() {
        val username = sharedPreferences.getString("username", "") ?: ""
        val password = sharedPreferences.getString("password", "") ?: ""

        if (username.isNotBlank() && password.isNotBlank()) {
            serviceScope.launch {
                sendLoginRequest(username, password)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE_SERVICE) {
            settingsPreferences.edit { putBoolean("isServiceEnabled", false) }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    companion object {
        private const val ACTION_DISABLE_SERVICE = "com.example.bitsgoaauto_login.DISABLE_SERVICE"
    }

}
