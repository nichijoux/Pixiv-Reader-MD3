package com.pixiv.reader.core.ui.component.card

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.model.toCardData

/** 保留此入口，兼容仍引用 core:ui 映射函数的调用方。 */
fun Novel.toCardData(): NovelCardData = toCardData()
