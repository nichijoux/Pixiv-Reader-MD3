package com.pixiv.reader.core.ui.component.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** 卡片内动图加载与播放；[maxDecodeSize] 应传封面宽（px）避免按原图解码浪费内存，离开视口自动取消下载。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UgoiraCardPlayer(
    loader: UgoiraLoader,
    illustId: Long,
    maxDecodeSize: Int,
    modifier: Modifier = Modifier,
) {
    // 按 illustId 绑定进度与帧缓存，避免列表项复用同一槽位时残留旧作品动图
    val progress = remember(illustId) { MutableStateFlow<Float?>(null) }
    val progressValue by progress.collectAsState()
    var frames by remember(illustId) { mutableStateOf<List<UgoiraFrame>?>(null) }

    LaunchedEffect(illustId) {
        // 快速滑过瀑布流时不立即拉取与解压 zip，停留超过 250ms 再开始准备帧
        delay(UGOIRA_CARD_LOAD_DEBOUNCE_MS)
        progress.value = 0f
        try {
            frames = loader.prepare(illustId) { p -> progress.value = p }
        } finally {
            progress.value = null
        }
    }

    val ready = frames
    if (!ready.isNullOrEmpty()) {
        UgoiraPlayer(
            frames = ready,
            modifier = modifier,
            maxDecodeSize = maxDecodeSize,
            contentScale = ContentScale.Crop,
        )
    } else {
        val p = progressValue
        if (p != null) {
            Box(
                modifier = modifier.background(ImageScrim),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    LoadingIndicator(color = Color.White)
                    Text(
                        text = "${(p * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private const val UGOIRA_CARD_LOAD_DEBOUNCE_MS = 250L
