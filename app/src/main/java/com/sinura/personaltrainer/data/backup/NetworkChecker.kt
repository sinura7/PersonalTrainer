package com.sinura.personaltrainer.data.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkChecker(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val network = connectivity?.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun requireOnline() {
        if (!isOnline()) {
            throw BackupException("Connect to the internet to use Google Drive.")
        }
    }
}
