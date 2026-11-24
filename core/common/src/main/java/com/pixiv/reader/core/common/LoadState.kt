package com.pixiv.reader.core.common

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Content<T>(val data: T) : LoadState<T>

    data class Error(val message: String? = null) : LoadState<Nothing>
}
