package com.mimo.remote.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val prefs = application.getSharedPreferences("mimo_remote", Context.MODE_PRIVATE)

    fun getServerUrl(): String {
        return prefs.getString("server_url", "ws://") ?: "ws://"
    }

    fun setServerUrl(url: String) {
        prefs.edit().putString("server_url", url).apply()
    }

    fun isAutoReconnect(): Boolean {
        return prefs.getBoolean("auto_reconnect", true)
    }

    fun setAutoReconnect(enabled: Boolean) {
        prefs.edit().putBoolean("auto_reconnect", enabled).apply()
    }
}
