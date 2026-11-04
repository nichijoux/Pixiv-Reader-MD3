package com.pixiv.reader.feature.comic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

/** 骨架网格列数（与真实榜单网格 [GRID_COLUMNS] 一致）。 */
private const val SKELETON_GRID_COLUMNS = 3

/** 骨架占位卡数量（三列 × 三行，铺满首屏）。 */
private const val SKELETON_CARD_COUNT = 9

/** 骨架封面宽高比（与 [ComicWorkCard] 的 CARD_COVER_RATIO 保持一致）。 */
private const val SKELETON_COVER_RATIO = 0.71f

/**
 * COMIC 首页加载骨架（「更新」与「排行」两页签共用）。
 *
 * 对齐两 Tab 的真实结构：`LazyVerticalGrid` 三列 + 同款内容边距；
 * [showRankingHeader] 为 true 时在网格顶部整行渲染排行头部占位
 * （模式分段条 + 分类标签横滑行），为 false 时渲染 banner 整行 + 分区标题条。
 * 呼吸脉冲占位替代全屏转圈，仅替换加载态渲染，不参与加载判定。
 *
 * @param showRankingHeader true = 排行 Tab 头部（模式分段 + 标签行）；false = 更新 Tab 头部（banner + 标题）
 * @param modifier 外部传入的 Modifier
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun ComicHomeSkeleton(
    showRankingHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = skeletonPulseColor(label = "comicHomeSkeleton")
    LazyVerticalGrid(
        columns = GridCells.Fixed(SKELETON_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (showRankingHeader) {
            // 排行头部：模式分段条（两段等宽）
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_skeleton_modes") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    repeat(2) {
                        SkeletonBlock(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(AppShapes.pill),
                            color = color,
                        )
                    }
                }
            }
            // 排行头部：分类标签横滑行（只渲染首行可见的 4 个占位）
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_skeleton_labels") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    repeat(4) {
                        SkeletonBlock(
                            modifier = Modifier.width(64.dp).height(32.dp).clip(AppShapes.cardSmall),
                            color = color,
                        )
                    }
                }
            }
        } else {
            // 更新头部：banner 整行（2:1，与 ComicBannerRow 常见比例一致）
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_skeleton_banner") {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .clip(AppShapes.cardLarge),
                    color = color,
                )
            }
            // 更新头部：分区标题条
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_skeleton_section_title") {
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .width(120.dp)
                        .height(18.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
            }
        }
        // 作品卡占位：封面（竖版书页比例）+ 标题条 + 作者条
        items(count = SKELETON_CARD_COUNT) {
            Column {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(SKELETON_COVER_RATIO)
                        .clip(AppShapes.card),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
            }
        }
    }
}
