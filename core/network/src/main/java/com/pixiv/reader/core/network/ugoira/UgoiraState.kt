package com.pixiv.reader.core.network.ugoira

sealed interface UgoiraState {
    data object Idle : UgoiraState

    data class Loading(val progress: Float = 0f) : UgoiraState

    data class Ready(val frames: List<UgoiraFrame>) : UgoiraState

    data object Failed : UgoiraState

    val isUgoira: Boolean
        get() = this !is Idle
}
