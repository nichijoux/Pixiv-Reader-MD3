package com.example.pixivapi.network

/**
 * pixiv 接口语言代码 holder（accept-language 头 / lang 查询参数）。
 *
 * 由应用层（[com.pixiv.reader.app.MainActivity.attachBaseContext]）在每次 attachBaseContext
 * 时根据应用语言设置写入。拦截器按请求实时读取，保证运行时语言切换即时生效。
 *
 * 默认 zh-CN（与历史行为一致）。
 */
object PixivLang {
    @Volatile
    var code: String = "zh-CN"
}