package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.NovelDefaultTab
import com.pixiv.reader.core.common.ViewerOrientation
import com.pixiv.reader.feature.user.R

/** 我的页「浏览设置」：小说默认页 / 插画查看方向（内容/浏览类偏好）。 */
@Composable
internal fun MeBrowseSection(
    novelDefaultTab: NovelDefaultTab,
    viewerOrientation: ViewerOrientation,
    onSetNovelDefaultTab: (NovelDefaultTab) -> Unit,
    onSetViewerOrientation: (ViewerOrientation) -> Unit,
) {
    // 小说默认页（进入小说 Tab 时显示推荐还是关注）
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_novel_default_tab),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                NovelDefaultTab.RECOMMEND to R.string.me_novel_default_recommend,
                NovelDefaultTab.FOLLOW to R.string.me_novel_default_follow,
            ).forEach { (value, labelRes) ->
                PillSelectButton(
                    selected = novelDefaultTab == value,
                    onClick = { onSetNovelDefaultTab(value) },
                    text = stringResource(labelRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    CardSpacer()
    // 插画查看方向（全屏查看器横向 / 竖向滑动切换）
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_viewer_orientation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ViewerOrientation.HORIZONTAL to R.string.me_viewer_orientation_horizontal,
                ViewerOrientation.VERTICAL to R.string.me_viewer_orientation_vertical,
                ViewerOrientation.SEAMLESS to R.string.me_viewer_orientation_seamless,
            ).forEach { (value, labelRes) ->
                PillSelectButton(
                    selected = viewerOrientation == value,
                    onClick = { onSetViewerOrientation(value) },
                    text = stringResource(labelRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
