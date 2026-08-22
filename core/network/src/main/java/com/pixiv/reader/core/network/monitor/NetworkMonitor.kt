package com.pixiv.reader.core.network.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 网络状态监听：基于 ConnectivityManager 的默认网络回调，提供可观察的在线状态。
 * 用于全局"网络连接已断开"提示。
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = checkNow()
                    }
                },
            )
        }
    }

    private fun checkNow(): Boolean {
        return runCatching {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
    }
}
