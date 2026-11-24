package com.pixiv.reader.feature.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * 用 Chrome Custom Tab 打开登录页（回调 scheme `pixiv://account/login`）；
 * 无 Custom Tabs 服务时降级为系统浏览器跳转。
 */
fun openLoginCustomTab(context: Context, url: String) {
    val uri = url.toUri()
    val builder = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    try {
        builder.launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}
