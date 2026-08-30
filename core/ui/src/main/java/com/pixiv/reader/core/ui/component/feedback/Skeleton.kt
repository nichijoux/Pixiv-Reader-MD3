package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Durations
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 骨架呼吸脉冲色：`surfaceVariant` + alpha 0.35↔0.75，替代全屏转圈的加载占位。
 * 全项目骨架统一动画源（搜索/排行榜/用户主页/小说页/插画页/评论区共用）。
 *
 * @param label 动画 label（同一组合树内多个骨架用于区分，默认足够）
 */
@Composable
fun skeletonPulseColor(label: String = "skeleton"): Color {
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Durations.PAGE_SWITCH_ANIM_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}Alpha",
    )
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
}

/**
 * 圆角占位块（[Modifier.clip] 由调用方指定形状）。
 *
 * @param modifier 尺寸/形状/背景由调用方通过 clip + 尺寸链组合
 * @param color 填充色（通常来自 [skeletonPulseColor]）
 */
@Composable
fun SkeletonBlock(modifier: Modifier, color: Color) {
    Box(modifier = modifier.background(color))
}

/**
 * 排行榜入口 banner 骨架占位：仿 [RankingList] 页顶排行榜入口（NovelRankingBanner / MangaRankingBanner）布局
 * ——48dp 图标块 + 两行文本条 + 右侧箭头块，位置/尺寸对齐真实入口，保证加载/刷新骨架阶段
 * 排行榜入口区域不"消失"。纯脉冲灰色，加载中不可点，数据到位后淡入真实 banner。
 */
@Composable
fun RankingBannerSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "rankingBannerSkeleton")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mdPlus, vertical = Spacing.xsPlus),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = AppShapes.card,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.mdPlus),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .size(Sizes.s48)
                    .clip(AppShapes.large),
                color = color,
            )
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(16.dp)
                        .clip(AppShapes.small),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.xsPlus)
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(AppShapes.small),
                    color = color,
                )
            }
            SkeletonBlock(
                modifier = Modifier.size(Sizes.s20),
                color = color,
            )
        }
    }
}
