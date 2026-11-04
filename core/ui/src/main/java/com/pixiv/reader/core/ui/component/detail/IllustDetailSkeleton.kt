package com.pixiv.reader.core.ui.component.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 插画详情完整加载骨架（深链进入 / 无 hero 种子兜底 / 排行榜右栏 pane 使用）。
 *
 * 顶部整宽 1:1 大图块 + [IllustInfoSkeleton] 信息区，呼吸脉冲占位替代全屏转圈；
 * 占位总高受 [Column] 自身内容约束，超出屏幕时随详情页一起可滚动。
 * 卡片进入的详情路由不再使用本组件——大图由 pager 的 seed 占位承载（hero 元素唯一），
 * 信息区单独用 [IllustInfoSkeleton] 拼接。
 *
 * @param modifier 外部传入的 Modifier（通常携带 Scaffold innerPadding）
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun IllustDetailSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "illustDetailSkeleton")
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部大图：与真实详情首图同比例（自适应列宽 × 1:1）
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AppShapes.cardLarge),
            color = color,
        )
        IllustInfoSkeleton()
    }
}

/**
 * 插画详情信息区骨架：标题两行 + 作者行 + 统计行 + 标签胶囊 + 相关作品横滑行。
 *
 * 从 [IllustDetailSkeleton] 拆出，供详情路由在 hero 占位大图之上拼接（大图由
 * [IllustPagePager] 的 seed 占位态承载，信息区数据未到时用本组件占位）。
 *
 * @param modifier 外部 Modifier（通常用于内边距控制）
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun IllustInfoSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "illustInfoSkeleton")
    Column(modifier = modifier.padding(Spacing.lg)) {
        // 标题两行（首行长、次行短）
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth(0.85f).height(16.dp).clip(AppShapes.tiny),
            color = color,
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .fillMaxWidth(0.55f)
                .height(16.dp)
                .clip(AppShapes.tiny),
            color = color,
        )
        // 作者行：圆头像 + 昵称条
        Row(
            modifier = Modifier.padding(top = Spacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(modifier = Modifier.size(Sizes.s24).clip(AppShapes.circle), color = color)
            SkeletonBlock(
                modifier = Modifier
                    .padding(start = Spacing.sm)
                    .width(120.dp)
                    .height(12.dp)
                    .clip(AppShapes.tiny),
                color = color,
            )
        }
        // 统计行：三块均分短条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
            }
        }
        // 标签胶囊：4 个等宽占位
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
        ) {
            repeat(4) {
                SkeletonBlock(
                    modifier = Modifier.width(56.dp).height(20.dp).clip(AppShapes.cardSmall),
                    color = color,
                )
            }
        }
        // 相关作品区：标题条 + 三张竖版卡横滑行
        SkeletonBlock(
            modifier = Modifier
                .padding(top = Spacing.lgPlus)
                .width(120.dp)
                .height(14.dp)
                .clip(AppShapes.tiny),
            color = color,
        )
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(100.dp)
                        .height(130.dp)
                        .clip(AppShapes.card),
                    color = color,
                )
            }
        }
    }
}
