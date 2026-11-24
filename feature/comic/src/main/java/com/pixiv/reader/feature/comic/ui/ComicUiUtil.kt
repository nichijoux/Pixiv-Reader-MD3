package com.pixiv.reader.feature.comic.ui

/**
 * 从站内作品 URL 解析 id。v1 仅支持作品页直达：活动页等其它链接返回 null（点击忽略）。
 */
fun parseComicWorkId(url: String?): Long? =
    url?.let { Regex("""/works/(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }

fun cleanComicDescription(html: String?): String =
    html.orEmpty()
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .trim()
