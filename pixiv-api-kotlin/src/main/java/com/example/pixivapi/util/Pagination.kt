package com.example.pixivapi.util

import com.example.pixivapi.Pageable
import com.example.pixivapi.api.AppApi
import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.IllustResponse
import com.example.pixivapi.model.Novel
import com.example.pixivapi.model.NovelResponse
import com.example.pixivapi.model.UserPreviewResponse

/**
 * 通用分页加载器
 *
 * Pixiv 列表接口统一返回 `next_url` 游标：
 * - 首屏：[loadInitial]
 * - 翻页：[loadNext]（请求 next_url）
 * - [hasMore] 判断是否到底
 *
 * 用法（View 层）：
 * ```
 * val page = PagedLoader { nextUrl, isFirst ->
 *     if (isFirst) api.getRanking(mode) else api.getNextIllusts(nextUrl)
 * }
 * val first = page.loadInitial()
 * if (page.hasMore) { val second = page.loadNext() }
 * ```
 */
class PagedLoader<T : Pageable<*>>(
    private val fetcher: suspend (nextUrl: String?, isFirstPage: Boolean) -> T,
) {

    private var currentPage: T? = null

    suspend fun loadInitial(): T {
        currentPage = fetcher(null, true)
        return currentPage!!
    }

    suspend fun loadNext(): T? {
        val next = currentPage?.nextPageUrl ?: return null
        currentPage = fetcher(next, false)
        return currentPage
    }

    val hasMore: Boolean get() = currentPage?.nextPageUrl != null
}

/** 便捷封装：插画分页 */
class IllustPagedLoader(
    private val api: AppApi,
    private val initial: suspend () -> IllustResponse,
) {
    private var nextUrl: String? = null
    private var current: List<Illust> = emptyList()

    suspend fun loadInitial(): List<Illust> {
        val resp = initial()
        current = resp.illusts
        nextUrl = resp.nextUrl
        return current
    }

    suspend fun loadMore(): List<Illust>? {
        val url = nextUrl ?: return null
        val resp = api.getNextIllusts(url)
        current = current + resp.illusts
        nextUrl = resp.nextUrl
        return resp.illusts
    }

    val hasMore: Boolean get() = nextUrl != null
    val allItems: List<Illust> get() = current
}

/** 便捷封装：小说分页 */
class NovelPagedLoader(
    private val api: AppApi,
    private val initial: suspend () -> NovelResponse,
) {
    private var nextUrl: String? = null
    private var current: List<Novel> = emptyList()

    suspend fun loadInitial(): List<Novel> {
        val resp = initial()
        current = resp.novels
        nextUrl = resp.nextUrl
        return current
    }

    suspend fun loadMore(): List<Novel>? {
        val url = nextUrl ?: return null
        val resp = api.getNextNovels(url)
        current = current + resp.novels
        nextUrl = resp.nextUrl
        return resp.novels
    }

    val hasMore: Boolean get() = nextUrl != null
    val allItems: List<Novel> get() = current
}

/** 便捷封装：用户分页 */
class UserPagedLoader(
    private val api: AppApi,
    private val initial: suspend () -> UserPreviewResponse,
) {
    private var nextUrl: String? = null
    private var current: List<com.example.pixivapi.model.UserPreview> = emptyList()

    suspend fun loadInitial(): List<com.example.pixivapi.model.UserPreview> {
        val resp = initial()
        current = resp.user_previews
        nextUrl = resp.nextUrl
        return current
    }

    suspend fun loadMore(): List<com.example.pixivapi.model.UserPreview>? {
        val url = nextUrl ?: return null
        val resp = api.getNextUsers(url)
        current = current + resp.user_previews
        nextUrl = resp.nextUrl
        return resp.user_previews
    }

    val hasMore: Boolean get() = nextUrl != null
    val allItems: List<com.example.pixivapi.model.UserPreview> get() = current
}
