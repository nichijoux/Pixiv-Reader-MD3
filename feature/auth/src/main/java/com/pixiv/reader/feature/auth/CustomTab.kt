package com.pixiv.reader.feature.auth

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** 使用 Chrome Custom Tab 打开登录页（回调 scheme pixiv://account/login） */
fun openLoginCustomTab(context: Context, url: String) {
    val builder = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    builder.launchUrl(context, url.toUri())
}
