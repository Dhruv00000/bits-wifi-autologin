package com.example.bitsgoaauto_login

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun log(message: String) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "[$timestamp] $message"
        android.util.Log.d("BITS_AUTO_LOGIN_DEBUG", formattedMessage)
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, formattedMessage) // Add to top
        if (currentLogs.size > 100) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _logs.value = currentLogs
    }

    fun clear() {
        _logs.value = emptyList()
    }

}
