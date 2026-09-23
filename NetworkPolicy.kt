package com.example.virtualcompanion.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.virtualcompanion.data.OfflineSettingsRepository

object NetworkPolicy {
    fun strictOffline(context: Context): Boolean = OfflineSettingsRepository(context).strictOffline()

    fun networkAvailable(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun cloudAllowed(context: Context): Boolean = !strictOffline(context) && networkAvailable(context)
}
