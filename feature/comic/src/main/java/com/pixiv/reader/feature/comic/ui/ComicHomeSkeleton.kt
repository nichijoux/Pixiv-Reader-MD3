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

/** 必须与真实网格 [GRID_COLUMNS] 一致。 */
private const val SKELETON_GRID_COLUMNS = 3

private const val SKELETON_CARD_COUNT = 9

/** 必须与 [ComicWorkCard] 的 CARD_COVER_RATIO 一致。 */
private const val SKELETON_COVER_RATIO = 0.71f

/** 两页签共用；只替换加载态渲染，不参与加载判定。 */
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
            // 2:1 与真实 banner 行一致。
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_skeleton_banner") {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .clip(AppShapes.cardLarge),
                    color = color,
                )
            }
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
