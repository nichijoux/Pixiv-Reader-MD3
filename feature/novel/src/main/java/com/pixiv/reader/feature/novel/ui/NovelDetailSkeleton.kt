package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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

/** 小说详情骨架正文列最大宽度（对齐详情内容列的阅读舒适宽度）。 */
private val SKELETON_CONTENT_MAX_WIDTH = 480.dp

/**
 * 小说详情加载骨架（全屏路由与 Master-Detail 右栏 pane 共用）。
 *
 * 对齐 [NovelDetailContent] 首屏结构：顶部沉浸式 banner 块（[NOVEL_BANNER_HEIGHT]）
 * + 居中正文列（标题 / 作者行 / 统计行 / 标签胶囊 / 简介三行），
 * 呼吸脉冲占位替代全屏转圈；只换加载态渲染，不参与任何加载判定。
 *
 * @param modifier 外部传入的 Modifier（通常携带 Scaffold innerPadding）
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun NovelDetailSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "novelDetailSkeleton")
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部 banner：与真实详情同高（沉浸式封面区）
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(NOVEL_BANNER_HEIGHT)
                .clip(AppShapes.cardLarge),
            color = color,
        )
        // 正文列：居中限宽，避免平板下统计/标签被拉得过散
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SKELETON_CONTENT_MAX_WIDTH)
                    .padding(Spacing.lg),
            ) {
                // 标题两行
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.9f).height(18.dp).clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                // 作者行：圆头像 + 昵称条 + 行尾关注胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        modifier = Modifier.size(Sizes.s36).clip(AppShapes.circle),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(start = Spacing.smPlus)
                            .width(120.dp)
                            .height(12.dp)
                            .clip(AppShapes.tiny),
                        color = color,
                    )
                    Box(Modifier.weight(1f))
                    SkeletonBlock(
                        modifier = Modifier.width(72.dp).height(28.dp).clip(AppShapes.pill),
                        color = color,
                    )
                }
                // 统计行：三块均分
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    repeat(3) {
                        SkeletonBlock(
                            modifier = Modifier.weight(1f).height(36.dp).clip(AppShapes.small),
                            color = color,
                        )
                    }
                }
                // 标签胶囊 × 4
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
                // 简介：三行文字条（末行更短）
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.mdPlus)
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
            }
        }
    }
}
