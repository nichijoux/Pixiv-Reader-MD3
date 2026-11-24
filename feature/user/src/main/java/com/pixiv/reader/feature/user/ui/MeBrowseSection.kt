package com.pixiv.reader.feature.user.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet

@Composable
internal fun MeBrowseSection(
    novelDefaultTab: NovelDefaultTab,
    viewerOrientation: ViewerOrientation,
    followSortMode: FollowSortMode,
    clipboardLinkPrompt: Boolean,
    novelFileNameTemplate: String,
    onSetNovelDefaultTab: (NovelDefaultTab) -> Unit,
    onSetViewerOrientation: (ViewerOrientation) -> Unit,
    onSetFollowSortMode: (FollowSortMode) -> Unit,
    onSetClipboardLinkPrompt: (Boolean) -> Unit,
    onOpenFileNameTemplate: () -> Unit,
) {
    MeGroupCard {
        MeSegmentedRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.me_novel_default_tab),
            selected = novelDefaultTab,
            options = listOf(
                NovelDefaultTab.RECOMMEND to R.string.me_novel_default_recommend,
                NovelDefaultTab.FOLLOW to R.string.me_novel_default_follow,
            ),
            accent = SettingAccentViolet,
            onSelect = onSetNovelDefaultTab,
        )
        MeSegmentedRow(
            icon = Icons.Filled.ScreenRotation,
            title = stringResource(R.string.me_viewer_orientation),
            selected = viewerOrientation,
            options = listOf(
                ViewerOrientation.HORIZONTAL to R.string.me_viewer_orientation_horizontal,
                ViewerOrientation.VERTICAL to R.string.me_viewer_orientation_vertical,
                ViewerOrientation.SEAMLESS to R.string.me_viewer_orientation_seamless,
            ),
            accent = SettingAccentBlue,
            onSelect = onSetViewerOrientation,
        )
        val sortOptions = listOf(
            FollowSortMode.FOLLOW_TIME to R.string.me_follow_sort_follow_time,
            FollowSortMode.NAME_ASC to R.string.me_follow_sort_name_asc,
            FollowSortMode.NAME_DESC to R.string.me_follow_sort_name_desc,
            FollowSortMode.LATEST_WORK to R.string.me_follow_sort_latest_work,
        )
        MeDropdownRow(
            icon = Icons.Filled.Sort,
            title = stringResource(R.string.me_follow_sort),
            currentValue = stringResource(sortOptions.first { it.first == followSortMode }.second),
            options = sortOptions,
            accent = SettingAccentTeal,
            onSelect = onSetFollowSortMode,
        )
        MeSwitchRow(
            icon = Icons.Filled.ContentPaste,
            title = stringResource(R.string.me_clipboard_link_prompt),
            subtitle = stringResource(R.string.me_clipboard_link_prompt_desc),
            checked = clipboardLinkPrompt,
            accent = SettingAccentGreen,
            onCheckedChange = onSetClipboardLinkPrompt,
        )
        MeRow(
            icon = Icons.Filled.Description,
            title = stringResource(R.string.me_novel_file_name_template),
            subtitle = novelFileNameTemplate,
            subtitleMaxLines = 1,
            accent = SettingAccentOrange,
            trailing = { MeArrowTrailing() },
            onClick = onOpenFileNameTemplate,
        )
    }
}
