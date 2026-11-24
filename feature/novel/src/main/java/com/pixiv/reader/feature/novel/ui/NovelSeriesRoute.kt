package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel

/**
 * 小说系列详情页：信息头 + 分册列表；内容区与下载弹窗/通知宿主复用 [NovelSeriesBody] + [NovelSeriesList]。
 * 底部沉浸式：Scaffold 不消耗系统栏 insets，列表底 padding 自行避让手势条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelSeriesRoute(
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: NovelSeriesViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    // 进程重建后保留（与系列页历史行为一致）
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

    NovelSeriesBody(
        viewModel = viewModel,
        showDownloadDialog = showDownloadDialog,
        onShowDownloadDialogChange = { showDownloadDialog = it },
    ) {
        Scaffold(
            // 不消耗系统栏 insets，内容背景延伸到导航栏后面
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = detail?.title ?: stringResource(R.string.novel_series_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.novel_cd_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            AdaptiveContentBox(modifier = Modifier.padding(padding)) {
                NovelSeriesList(
                    viewModel = viewModel,
                    onOpenNovel = onOpenNovel,
                    onOpenCover = onOpenCover,
                    onOpenUser = onOpenUser,
                    onSearchTag = onSearchTag,
                    onOpenSeries = onOpenSeries,
                    onDownloadClick = { showDownloadDialog = true },
                )
            }
        }
    }
}
