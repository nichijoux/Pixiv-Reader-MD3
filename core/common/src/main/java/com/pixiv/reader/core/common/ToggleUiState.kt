package com.pixiv.reader.core.common

/**
 * 布尔开关四态机：请求进行中（`TURNING_*`）的 [isOn] 保留翻转前旧值，仅经 [inFlight] 禁用防连点；
 * 由 `MessageViewModel.runToggle` 驱动（失败回滚原终态）。
 */
enum class ToggleUiState {
    OFF,
    ON,
    TURNING_ON,
    TURNING_OFF;

    val isOn: Boolean
        get() = this == ON || this == TURNING_OFF

    val inFlight: Boolean
        get() = this == TURNING_ON || this == TURNING_OFF
}
