package com.pixiv.reader.core.ui.component.card

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.model.toCardData

/** re-export：映射实现已下沉 core:common，此处保持既有 import 不变。 */
fun Novel.toCardData(): NovelCardData = toCardData()
