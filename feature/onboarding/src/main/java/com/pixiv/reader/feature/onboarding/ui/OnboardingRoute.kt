package com.pixiv.reader.feature.onboarding.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixiv.reader.feature.onboarding.R
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import kotlinx.coroutines.launch

/** 引导页单页内容：图标 + 标题 + 描述（图标仅装饰，无内容描述）。 */
private data class OnboardingPage(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        icon = Icons.Filled.Explore,
        titleRes = R.string.onboarding_title_discover,
        descriptionRes = R.string.onboarding_desc_discover,
    ),
    OnboardingPage(
        icon = Icons.AutoMirrored.Filled.MenuBook,
        titleRes = R.string.onboarding_title_reader,
        descriptionRes = R.string.onboarding_desc_reader,
    ),
    OnboardingPage(
        icon = Icons.Filled.Favorite,
        titleRes = R.string.onboarding_title_bookmark,
        descriptionRes = R.string.onboarding_desc_bookmark,
    ),
)

/**
 * 首次启动引导页：三页横向滑动介绍 + 圆点指示器 + 底部主按钮（下一步/开始使用）。
 * 无状态无注入：完成/跳过通过 [onFinished] 上抛，由调用方写入 DataStore 并跳转。
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == ONBOARDING_PAGES.size - 1

    Column(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        // 顶部：跳过（最后一页隐藏，避免与「开始使用」重复）
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Crossfade(targetState = lastPage, label = "skip_button") { isLast ->
                if (!isLast) {
                    TextButton(onClick = onFinished) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
        }

        // 中间：三页介绍
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val item = ONBOARDING_PAGES[page]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(140.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.s64),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 底部：指示器 + 主按钮（固定高度容器，文字切换无跳变）
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(ONBOARDING_PAGES.size) { index ->
                    val selected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        label = "onboarding_dot_$index",
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (lastPage) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    text = stringResource(if (lastPage) R.string.onboarding_start else R.string.onboarding_next),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
