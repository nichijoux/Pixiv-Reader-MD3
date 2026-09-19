package com.pixiv.reader.core.common

/**
 * 布尔型开关动作的 UI 状态机（收藏 / 关注 / 追更 / 拉黑 / 已读标记等）。
 *
 * 取代此前「状态 + 请求进行中」两个独立布尔的组合：单一流承载全部四个合法组合，
 * 非法组合（如进行中却丢失原状态）从类型上不可表示；请求进行中保留旧状态语义
 * （[isOn] 在 TURNING_* 时仍取翻转前的值），按钮文案不变、仅禁用防连点。
 *
 * 状态转移由 core:network `MessageViewModel.runToggle` 骨架统一驱动：
 * OFF ⇄ ON 为成功终态；发起请求写入 TURNING_ON / TURNING_OFF，失败回滚原终态。
 */
enum class ToggleUiState {
    /** 关（未收藏 / 未关注 / 未追更），空闲。 */
    OFF,

    /** 开（已收藏 / 已关注 / 已追更），空闲。 */
    ON,

    /** 关 → 开请求进行中（目标态开；失败回滚 OFF）。 */
    TURNING_ON,

    /** 开 → 关请求进行中（目标态关；失败回滚 ON）。 */
    TURNING_OFF;

    /** 当前展示态：进行中保留旧值（TURNING_OFF 视为开、TURNING_ON 视为关）。 */
    val isOn: Boolean
        get() = this == ON || this == TURNING_OFF

    /** 请求进行中（防连点依据，进行中禁用操作按钮）。 */
    val inFlight: Boolean
        get() = this == TURNING_ON || this == TURNING_OFF
}
