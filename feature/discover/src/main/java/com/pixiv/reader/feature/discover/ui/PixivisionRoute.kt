package com.pixiv.reader.feature.discover.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Article
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.PixivisionViewModel

/**
 * pixivision 特辑列表（路由 `pixivision`）：沉浸式布局——无独立顶栏，状态栏区域与
 * 页面同色延伸，自绘顶行（返回钮 + 大标题 + 副标语），文章封面卡列表。
 *
 * pixivision 无正文 app API：文章卡点击跳系统浏览器打开原文，
 * 触底分页（`getArticles` + `getNextArticles`）。
 *
 * @param onBack 返回
 */
@Composable
fun PixivisionRoute(
    onBack: () -> Unit,
    viewModel: PixivisionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 沉浸式顶行：状态栏同色延伸，返回钮 + 大标题 + 副标语
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    AppShapes.circle,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
            Column {
                Text(
                    text = "pixivision",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.pixivision_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AdaptiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(
                    message = error.orEmpty(),
                    onRetry = viewModel::load,
                )
                items.isEmpty() -> EmptyBox(stringResource(R.string.pixivision_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(items, key = { it.id }) { article ->
                        PixivisionArticleCard(
                            article = article,
                            onClick = {
                                // pixivision 无正文 app API：文章原文跳系统浏览器
                                article.article_url?.let { url ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                                    )
                                }
                            },
                        )
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
                        }
                    }
                }
            }
        }
    }
}

/** 特辑文章卡：左侧封面（横幅比例裁剪）+ 右侧标题/分类标签/日期。 */
@Composable
private fun PixivisionArticleCard(
    article: Article,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            PixivImage(
                url = article.thumbnail,
                contentDescription = article.pure_title ?: article.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = Spacing.md)
                .weight(1f),
        ) {
            Text(
                text = article.pure_title ?: article.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            article.subcategory_label?.takeIf { it.isNotBlank() }?.let { label ->
                Surface(
                    modifier = Modifier.padding(top = Spacing.xs),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = AppShapes.small,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                    )
                }
            }
            article.publish_date?.take(10)?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
