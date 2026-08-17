package com.example.bitsgoaauto_login

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            application,
            "secret_shared_prefs",
            MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val workManager = WorkManager.getInstance(application)
    private val _isServiceEnabled =
        MutableStateFlow(sharedPreferences.getBoolean("isServiceEnabled", true))
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()
    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()
    private val _isWifiValidated = MutableStateFlow(false)
    val isWifiValidated: StateFlow<Boolean> = _isWifiValidated.asStateFlow()
    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()
    private val _isCaptivePortal = MutableStateFlow(false)
    val isCaptivePortal: StateFlow<Boolean> = _isCaptivePortal.asStateFlow()
    private val _hasCredentials = MutableStateFlow(
        sharedPreferences.contains("username") && !sharedPreferences.getString(
            "username",
            ""
        ).isNullOrEmpty()
    )
    val hasCredentials: StateFlow<Boolean> = _hasCredentials.asStateFlow()
    private val _loginResult = MutableStateFlow<Result<String>?>(null)
    val loginResult: StateFlow<Result<String>?> = _loginResult.asStateFlow()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            _isWifiConnected.value = true
        }

        override fun onLost(network: Network) {
            _isWifiConnected.value = false
            _isWifiValidated.value = false
            _isCaptivePortal.value = false
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isWifiValidated.value =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            var currentSsid = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = capabilities.transportInfo as? WifiInfo
                val ssidValue = wifiInfo?.ssid?.replace("\"", "")
                if (ssidValue != null && ssidValue != "<unknown ssid>") {
                    currentSsid = ssidValue
                } else {
                    @Suppress("DEPRECATION")
                    currentSsid = wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
                }
            } else {
                @Suppress("DEPRECATION")
                currentSsid = wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
            }
            _ssid.value = currentSsid
            _isCaptivePortal.value = isCampusNetwork(currentSsid) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        }
    }

    init {
        connectivityManager.registerNetworkCallback(
            (NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()), networkCallback
        )
        updateServiceAndWorker()
    }

    fun toggleService(enabled: Boolean) {
        _isServiceEnabled.value = enabled
        sharedPreferences.edit { putBoolean("isServiceEnabled", enabled) }
        updateServiceAndWorker()
    }

    private fun updateServiceAndWorker() {

        val enabled = _isServiceEnabled.value
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, AutoLoginService::class.java)

        if (enabled) {
            context.startForegroundService(serviceIntent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val suggestions = CAMPUS_SSIDS.map { ssid ->
                    val builder = WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)
                        .setIsAppInteractionRequired(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        builder.setIsInitialAutojoinEnabled(true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setPriority(100)
                    }
                    builder.build()
                }
                try {
                    wifiManager.addNetworkSuggestions(suggestions)
                } catch (_: Exception) {
                    // Exceptions are ignored here because, in some cases (like when Wi-Fi is turned off), an exception is thrown, and the app doesn't need to care about that.
                }
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            workManager.enqueueUniquePeriodicWork(
                "HeartbeatWork",
                ExistingPeriodicWorkPolicy.KEEP,
                heartbeatRequest
            )
        } else {
            context.stopService(serviceIntent)
            workManager.cancelUniqueWork("HeartbeatWork")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiManager.removeNetworkSuggestions(emptyList<WifiNetworkSuggestion>())
            }
        }

    }

    fun manualLogin() {
        val username = getStoredUsername()
        val password = getStoredPassword()
        if (username.isBlank() || password.isBlank()) {
            _loginResult.value = Result.failure(Exception("Please save credentials first"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = sendLoginRequest(username, password)
            _loginResult.value = result
        }
    }

    fun clearLoginResult() {
        _loginResult.value = null
    }

    fun saveCredentials(username: String, password: String) {
        sharedPreferences.edit {
            putString("username", username)
            putString("password", password)
        }
        _hasCredentials.value = true
    }

    fun deleteCredentials() {
        sharedPreferences.edit { clear() }
        _hasCredentials.value = false
        sharedPreferences.edit { putBoolean("isServiceEnabled", _isServiceEnabled.value) }
    }

    fun refreshConnectivity() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun getStoredUsername(): String = sharedPreferences.getString("username", "") ?: ""
    fun getStoredPassword(): String = sharedPreferences.getString("password", "") ?: ""

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    companion object {
        private val CAMPUS_KEYWORDS =
            listOf("BPGC") // TODO: Add more keywords (if you find any).
        val CAMPUS_SSIDS = listOf( // TODO: Add more networks (if you find any).
            "BPGC_AUDI",
            "BPGC-WIFI",
            "BPGC-NAB",
            "BPGC",
            "BPGC-DH",
            "BPGC-A_HOSTEL",
            "BPGC-C_HOSTEL"
        )
        fun isCampusNetwork(ssid: String?): Boolean {
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") return false
            return CAMPUS_KEYWORDS.any { ssid.contains(it, ignoreCase = true) }
        }
    }

}
