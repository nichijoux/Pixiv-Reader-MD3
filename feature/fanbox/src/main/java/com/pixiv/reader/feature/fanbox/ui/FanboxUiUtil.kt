package com.pixiv.reader.feature.fanbox.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * FANBOX ISO 时间截断展示（`2026-08-01T12:34:56+09:00` → `2026-08-01 12:34`）。
 * 首页投稿卡 / 帖子 header / 评论行共用。
 *
 * @param iso 服务端原始时间串
 * @return 截断时间；入参异常原样返回
 */
internal fun formatFanboxDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val t = iso.indexOf('T')
        if (t <= 0) iso else "${iso.substring(0, t)} ${iso.substring(t + 1, minOf(t + 6, iso.length))}"
    }.getOrDefault(iso)
}

/**
 * FANBOX 链接统一路由（PRD FR-8）：fanbox.cc（含子域）→ App 内可见 WebView；站外 → 系统浏览器。
 * 帖子详情全屏页 / 平板详情 pane 共用。
 *
 * @param context 用于发起站外浏览器 Intent 的 Context
 * @param url 目标链接
 * @param title WebView 标题（fanbox.cc 域内跳转时展示）
 * @param onOpenWeb 打开 App 内可见 WebView（url, title）
 * @return 无返回值
 */
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
