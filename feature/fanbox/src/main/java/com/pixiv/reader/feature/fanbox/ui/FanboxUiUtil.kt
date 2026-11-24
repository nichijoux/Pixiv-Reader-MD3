package com.pixiv.reader.feature.fanbox.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** ISO 时间截断展示（`2026-08-01T12:34:56+09:00` → `2026-08-01 12:34`），入参异常原样返回。 */
internal fun formatFanboxDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val t = iso.indexOf('T')
        if (t <= 0) iso else "${iso.substring(0, t)} ${iso.substring(t + 1, minOf(t + 6, iso.length))}"
    }.getOrDefault(iso)
}

/** fanbox.cc（含子域）进 App 内可见 WebView，站外走系统浏览器。 */
internal fun openFanboxLink(
    context: Context,
    url: String,
    title: String,
    onOpenWeb: (String, String) -> Unit,
) {
    val host = runCatching { Uri.parse(url).host }.getOrNull()
    if (host == "fanbox.cc" || host?.endsWith(".fanbox.cc") == true) {
        onOpenWeb(url, title)
    } else {
        // 站外链接交系统浏览器（解析/启动失败静默忽略）
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
