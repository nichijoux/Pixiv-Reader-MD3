package com.pixiv.reader.feature.comic.ui

/**
 * COMIC UI 杂项工具（纯函数）。
 */

/**
 * 从 banner 等站内 URL 解析作品 id（如 `https://comic.pixiv.net/works/12052` → 12052）。
 * v1 仅支持站内作品页直达；活动页等其它链接不可解析返回 null（点击忽略）。
 *
 * @param url 站内链接
 * @return 作品 id；无法解析返回 null
 */
fun parseComicWorkId(url: String?): Long? =
    url?.let { Regex("""/works/(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }

/**
 * 清洗作品简介富文本：`<br>` 系列换行转真实换行，其余标签剔除。
 *
 * @param html 服务端简介（内嵌 `<br>`；可为 null）
 * @return 纯文本简介；输入 null 返回空串
 */
fun cleanComicDescription(html: String?): String =
    html.orEmpty()
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .trim()
