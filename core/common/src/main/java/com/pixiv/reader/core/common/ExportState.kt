package com.pixiv.reader.core.common

sealed interface ExportState {
    data object Idle : ExportState

    data class Running(val status: String) : ExportState

    val isRunning: Boolean
        get() = this is Running

    val statusText: String?
        get() = (this as? Running)?.status
}
