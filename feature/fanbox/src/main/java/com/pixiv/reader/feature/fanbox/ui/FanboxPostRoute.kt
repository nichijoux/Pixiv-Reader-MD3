package com.pixiv.reader.feature.fanbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixiv.api.model.FanboxComment
import com.pixiv.api.model.FanboxPost
import com.pixiv.reader.core.common.format.formatFileSize
import com.pixiv.reader.core.network.fanbox.FanboxHeaderInterceptor
import com.pixiv.reader.core.network.fanbox.FanboxSection
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.fanbox.R
import com.pixiv.reader.feature.fanbox.state.FanboxCommentsState
import com.pixiv.reader.feature.fanbox.state.FanboxPlansState
import com.pixiv.reader.feature.fanbox.state.FanboxPostState
import com.pixiv.reader.feature.fanbox.state.FanboxPostViewModel

/**
 * FANBOX 帖子详情：header（封面 / 标题 / 创作者）→ 正文段（post.info，失败退 post.get
 * 仅元数据）→ 受限帖赞助方案 → 评论（楼中楼）。各段独立失败不塌整页。
 *
 * 链接路由（PRD FR-8）：fanbox.cc 域进可见 WebView，站外走系统浏览器。
 *
 * @param onBack 返回上一页
 * @param onOpenWeb 打开 App 内可见 WebView（url, title）
 * @param onOpenImage 打开全屏图片预览（url, title）
 * @param viewModel 页面状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxPostRoute(
    onBack: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    viewModel: FanboxPostViewModel = hiltViewModel(),
) {
    val postState by viewModel.post.collectAsStateWithLifecycle()
    val plansState by viewModel.plans.collectAsStateWithLifecycle()
    val commentsState by viewModel.comments.collectAsStateWithLifecycle()
    val notificationHost = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHost)
    val context = LocalContext.current

    // 帖子网页地址（兜底「在网页中打开」与外链路由共用判定）
    val postUrl = "${FanboxHeaderInterceptor.FANBOX_URL}posts/${viewModel.postId}"
    val title = postState.post?.title.orEmpty().ifEmpty { stringResource(R.string.fanbox_post_title) }

    /** 链接统一路由：fanbox.cc → 可见 WebView；站外 → 系统浏览器（共享实现见 [openFanboxLink]）。 */
    fun openLink(url: String) = openFanboxLink(context, url, title, onOpenWeb)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fanbox_cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenWeb(postUrl, title) }) {
                        Icon(
                            Icons.Filled.OpenInBrowser,
                            contentDescription = stringResource(R.string.fanbox_open_in_web),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { NotificationHost(notificationHost) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        FanboxPostContent(
            postState = postState,
            plansState = plansState,
            commentsState = commentsState,
            onRetry = viewModel::retry,
            onOpenWeb = onOpenWeb,
            onOpenImage = onOpenImage,
            openLink = ::openLink,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * 帖子详情内容块（不含 Scaffold/TopAppBar）：全屏路由与平板详情 pane 共用。
 *
 * @param postState 详情状态（元数据 + 正文段）
 * @param plansState 赞助方案段状态
 * @param commentsState 评论段状态
 * @param onRetry 整页重试
 * @param onOpenWeb 打开可见 WebView
 * @param onOpenImage 打开全屏图片
 * @param openLink 站内/站外链接统一路由
 * @param modifier 外部传入的 Modifier（通常带 padding）
 */
@Composable
internal fun FanboxPostContent(
    postState: FanboxPostState,
    plansState: FanboxPlansState,
    commentsState: FanboxCommentsState,
    onRetry: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    openLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveContentBox(modifier = modifier) {
        when {
            postState.isLoading -> LoadingBox()
            postState.error != null -> ErrorBox(message = postState.error, onRetry = onRetry)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                postState.post?.let { post ->
                    item(key = "header") { PostHeader(post = post, onOpenWeb = onOpenWeb) }
                    item(key = "body_notice") { BodyNotice(postState = postState) }

                    postState.sections.forEachIndexed { index, section ->
                        item(key = "section_$index") {
                            SectionContent(
                                section = section,
                                onOpenImage = onOpenImage,
                                openLink = openLink,
                            )
                        }
                    }

                    if (post.isRestricted) {
                        item(key = "plans") {
                            PlansSection(
                                state = plansState,
                                plansUrl = "${FanboxHeaderInterceptor.FANBOX_URL}@${post.creatorId.orEmpty()}/plans",
                                creatorName = post.user?.name.orEmpty(),
                                onOpenWeb = onOpenWeb,
                            )
                        }
                    }
                    item(key = "comments") {
                        CommentsSection(state = commentsState)
                    }
                }
            }
        }
    }
}

/** 详情 header：封面 + 标题 + 创作者行（点击进创作者网页）。 */
@Composable
private fun PostHeader(post: FanboxPost, onOpenWeb: (String, String) -> Unit) {
    val fallbackTitle = stringResource(R.string.fanbox_post_title)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (post.coverUrl.isNotEmpty()) {
            PixvImage16to9(url = post.coverUrl, description = post.title)
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Text(
                text = post.title.orEmpty().ifEmpty { fallbackTitle },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (post.feeRequired > 0) {
                Text(
                    text = stringResource(R.string.fanbox_fee_required, post.feeRequired),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !post.creatorId.isNullOrBlank()) {
                        post.creatorId?.let { id ->
                            onOpenWeb("${FanboxHeaderInterceptor.FANBOX_URL}@$id", post.user?.name.orEmpty())
                        }
                    }
                    .padding(vertical = Spacing.sm),
            ) {
                UserAvatar(
                    name = post.user?.name,
                    avatarUrl = post.user?.iconUrl,
                    modifier = Modifier.size(Sizes.s28),
                )
                Text(
                    text = post.user?.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                )
                Text(
                    text = formatFanboxDate(post.publishedDatetime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 正文前置提示：受限帖说明 / 元数据兜底说明 / 均正常则不渲染。 */
@Composable
private fun BodyNotice(postState: FanboxPostState) {
    val post = postState.post ?: return
    val message = when {
        post.isRestricted -> stringResource(R.string.fanbox_restricted_title)
        postState.bodyFallback -> stringResource(R.string.fanbox_body_fallback)
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.s18),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/**
 * 单个正文段渲染：段落（链接/加粗）/ 标题 / 图片（点击全屏）/ 附件 / 嵌入占位。
 */
@Composable
private fun SectionContent(
    section: FanboxSection,
    onOpenImage: (String, String) -> Unit,
    openLink: (String) -> Unit,
) {
    when (section) {
        is FanboxSection.Paragraph -> ParagraphText(section, openLink = openLink)
        is FanboxSection.Header -> Text(
            text = section.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        is FanboxSection.Image -> BodyImage(section, onOpenImage = onOpenImage)
        is FanboxSection.File -> FileCard(section)
        is FanboxSection.Embed -> EmbedCard(section, openLink = openLink)
    }
}

/** 段落：链接（fanbox.cc 进 WebView / 站外系统浏览器）与加粗区间内联渲染。 */
@Composable
private fun ParagraphText(section: FanboxSection.Paragraph, openLink: (String) -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(section.text)
        section.boldSpans.forEach { span ->
            if (span.length > 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), span.offset, span.offset + span.length)
            }
        }
        section.links.forEach { link ->
            val url = link.url ?: return@forEach
            if (link.length > 0) {
                addLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { openLink(url) },
                    ),
                    link.offset,
                    link.offset + link.length,
                )
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xsPlus),
    )
}

/** 正文图片：有宽高按原比例，否则 16:9 占位；点击进全屏预览。 */
@Composable
private fun BodyImage(section: FanboxSection.Image, onOpenImage: (String, String) -> Unit) {
    val url = section.url ?: section.thumbnailUrl
    if (url.isNullOrBlank()) return
    val ratio = if (section.width > 0 && section.height > 0) {
        section.width.toFloat() / section.height.toFloat()
    } else {
        16f / 9f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clickable { onOpenImage(url, url.substringAfterLast('/')) },
    ) {
        PixivImage(
            url = url,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        )
    }
}

/** 附件卡：名称 + 大小（点击无动作，下载走网页）。 */
@Composable
private fun FileCard(section: FanboxSection.File) {
    Card(
        shape = RoundedCornerShape(Spacing.md),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.s20),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(
                    text = section.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.size > 0) {
                    Text(
                        text = formatFileSize(section.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.fanbox_file_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 嵌入占位卡：服务商标签 + 跳转（无 URL 仅展示）。 */
@Composable
private fun EmbedCard(section: FanboxSection.Embed, openLink: (String) -> Unit) {
    val url = section.url
    Card(
        shape = RoundedCornerShape(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clickable(enabled = !url.isNullOrBlank()) { url?.let(openLink) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.s20),
            )
            Text(
                text = section.label ?: stringResource(R.string.fanbox_embed_open),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.fanbox_embed_open),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 赞助方案段（受限帖）：标题 + 方案卡（费用 / 说明 / 加入按钮 → 网页）。 */
@Composable
private fun PlansSection(
    state: com.pixiv.reader.feature.fanbox.state.FanboxPlansState,
    plansUrl: String,
    creatorName: String,
    onOpenWeb: (String, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Text(
            text = stringResource(R.string.fanbox_plans_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().height(96.dp)) { LoadingBox(Modifier.fillMaxSize()) }
            state.error != null -> Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            state.items.isEmpty() -> Unit
            else -> state.items.forEach { plan ->
                Card(
                    shape = RoundedCornerShape(Spacing.md),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plan.title.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.fanbox_plan_fee, plan.fee),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // description 跨模块属性，先落局部变量再判空（isNullOrBlank 后无法智能转换）
                        val planDescription = plan.description
                        if (!planDescription.isNullOrBlank()) {
                            Text(
                                text = planDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                        Button(
                            onClick = { onOpenWeb(plansUrl, creatorName) },
                            modifier = Modifier.padding(top = Spacing.sm).align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.fanbox_plan_join))
                        }
                    }
                }
            }
        }
    }
}

/** 评论段：锁定（赞助门槛）/ 加载 / 空 / 列表（楼中楼缩进）。 */
@Composable
private fun CommentsSection(state: FanboxCommentsState) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Text(
            text = stringResource(R.string.fanbox_comments_title, state.items.sumOf { 1 + it.replies.orEmpty().size }),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        when {
            state.locked -> LockedNotice()
            state.isLoading -> Box(Modifier.fillMaxWidth().height(96.dp)) { LoadingBox(Modifier.fillMaxSize()) }
            state.error != null -> Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            state.items.isEmpty() -> Text(
                text = stringResource(R.string.fanbox_comments_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            else -> state.items.forEach { comment ->
                CommentRow(comment = comment, indent = false)
                comment.replies.orEmpty().forEach { reply ->
                    CommentRow(comment = reply, indent = true)
                }
            }
        }
    }
}

/** 赞助门槛锁定提示（PLEDGE_INSUFFICIENT）。 */
@Composable
private fun LockedNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.s18),
        )
        Text(
            text = stringResource(R.string.fanbox_comments_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** 评论行：头像 + 名字 + 时间 + 内容；楼中楼缩进展示。 */
@Composable
private fun CommentRow(comment: FanboxComment, indent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (indent) Spacing.xl else Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xs,
            ),
    ) {
        UserAvatar(
            name = comment.user?.name,
            avatarUrl = comment.user?.iconUrl,
            modifier = Modifier.size(if (indent) Sizes.s20 else Sizes.s28),
        )
        Column(modifier = Modifier.padding(start = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = formatFanboxDate(comment.createdDatetime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = comment.body.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

/** 16:9 封面图（header 顶部）。 */
@Composable
private fun PixvImage16to9(url: String, description: String?) {
    PixivImage(
        url = url,
        contentDescription = description,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}
