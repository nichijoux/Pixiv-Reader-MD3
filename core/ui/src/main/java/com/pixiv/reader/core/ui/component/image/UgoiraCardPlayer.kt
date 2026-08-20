package com.pixiv.reader.core.ui.component.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ugoira 卡片播放：加载帧（进程内缓存命中后立即播放）→ [UgoiraPlayer] 逐帧动画。
 *
 * 下载反馈：zip 下载中（[UgoiraLoader.prepare] 进度回调）在静态封面之上显示
 * 半透明黑底 + 转圈 + 百分比；帧就绪后切换为动画播放；下载失败/未开始透明露出封面。
 * 离开视口随 item 组合销毁（协程取消，下载中断）。
 *
 * @param loader 动图加载器（上层注入）
 * @param illustId 作品 id（zip/帧缓存与 metadata 均按 id）
 * @param maxDecodeSize 帧采样解码最长边上限（px），传封面宽度避免解码原图浪费内存
 */
@Composable
fun UgoiraCardPlayer(
    loader: UgoiraLoader,
    illustId: Long,
    maxDecodeSize: Int,
    modifier: Modifier = Modifier,
) {
    // 进度用 StateFlow（线程安全）：zip 下载回调来自 IO 线程，Compose mutableStateOf 跨线程写会抛异常
    val progress = remember { MutableStateFlow<Float?>(null) }
    val progressValue by progress.collectAsState()
    var frames by remember { mutableStateOf<List<UgoiraFrame>?>(null) }

    LaunchedEffect(illustId) {
        progress.value = 0f
        frames = loader.prepare(illustId) { p -> progress.value = p }
        progress.value = null
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
            // zip 下载中：半透明黑底 + 转圈 + 百分比（帧就绪后由播放器替换）
            Box(
                modifier = modifier.background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "${(p * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
        // p == null：metadata 请求中 / 下载失败 / 帧未就绪 → 透明露出静态封面
    }
}
