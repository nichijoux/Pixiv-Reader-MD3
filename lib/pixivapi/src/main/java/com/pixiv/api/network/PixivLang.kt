package com.pixiv.api.network

/**
 * accept-language 头 / lang 查询参数的语言代码。由 app 层每次 attachBaseContext 写入，
 * 拦截器按请求实时读取，保证运行时切换即时生效；默认 zh-CN。
 */
object PixivLang {
    @Volatile
    var code: String = "zh-CN"
}