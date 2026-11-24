package com.pixiv.reader.core.comment.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Stamp
import com.pixiv.reader.core.comment.R
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.component.layout.LocalBottomNavBarStyle
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import kotlinx.coroutines.launch

/**
 * 评论输入框（插画/小说详情评论区共用）：主行 = 表情按钮 + 富文本输入框 + 发送。表情按钮弹**底部停靠表情栏**
 * （IME 式：面板占屏幕底部、输入行保持在其正上方，与软键盘互斥切换），栏内分页切换文本表情 / 贴纸，
 * 系统返回键优先收起面板。表情与 `@昵称 ` 提及在缓冲区各占一个占位字符，对外草稿仍是含 `(xxx)` 的纯文本
 * （API 协议不变）；贴纸选后直接发送（`stamp_id`）。
 */
@Composable
fun CommentInput(
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier,
    stamps: List<Stamp> = emptyList(),
    onStampPick: ((Stamp) -> Unit)? = null,
    mentionName: String? = null,
    style: BottomNavBarStyle = LocalBottomNavBarStyle.current,
) {
    var panelExpanded by remember { mutableStateOf(false) }
    val handle = remember { EmoteFieldHandle() }
    // 发送成功确认触觉（草稿为空时 onPost 由外部拦截，这里也只在非空时响）
    val haptic = rememberHapticCue()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    // 面板高度对齐键盘：记住上次键盘可视高度（扣除导航栏），使 键盘↔面板 切换时输入行不跳动；
    // 本次会话尚未弹过键盘时用默认值兜底
    var lastKeyboardHeightDp by rememberSaveable { mutableIntStateOf(0) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeVisible = imeBottomPx > navBarBottomPx
    LaunchedEffect(imeBottomPx) {
        // 面板展开期间冻结记录：打开面板触发的收起动画若持续刷新，
        // 记忆值会被压到接近 0，面板随之塌缩（表现为“唤不出”）
        if (!panelExpanded && imeVisible) {
            lastKeyboardHeightDp = imeBottomPx - navBarBottomPx
        }
    }
    val panelHeight = if (lastKeyboardHeightDp > 0) {
        with(density) { lastKeyboardHeightDp.toDp() }
    } else {
        DefaultPanelHeight
    }

    fun closePanel(showKeyboard: Boolean) {
        if (!panelExpanded) return
        panelExpanded = false
        if (showKeyboard) {
            handle.view?.requestFocus()
            keyboard?.show()
        }
    }

    // 点回复后：聚焦输入框并唤起键盘，离去置于缓冲区末尾（胶囊之后）
    LaunchedEffect(mentionName) {
        if (!mentionName.isNullOrBlank()) {
            handle.view?.requestFocus()
            keyboard?.show()
        }
    }

    // 系统返回键优先收起面板而非退出页面
    BackHandler(enabled = panelExpanded) { closePanel(showKeyboard = false) }

    // 悬浮胶囊模式在表情面板展开时平滑过渡为贴边停靠承载面板，同一组合树不重建输入框。
    val isFloatingPill = style == BottomNavBarStyle.FLOATING_CAPSULE && !panelExpanded
    val outerHorizontalPadding by animateDpAsState(
        targetValue = if (isFloatingPill) Spacing.lg else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "commentBarHPadding",
    )
    val outerBottomPadding by animateDpAsState(
        targetValue = if (isFloatingPill) {
            if (imeVisible) Spacing.sm else Spacing.md
        } else {
            0.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "commentBarBPadding",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isFloatingPill) 28.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "commentBarCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isFloatingPill) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "commentBarColor",
    )
    val surfaceShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFloatingPill) {
                    // 悬浮模式下在胶囊外消费导航栏与键盘 inset，让胶囊底部与两侧保持透明透出列表
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = outerHorizontalPadding)
                        .padding(bottom = outerBottomPadding)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .then(if (isFloatingPill) Modifier.widthIn(max = FloatingInputMaxWidth) else Modifier)
                .fillMaxWidth(),
            shape = surfaceShape,
            color = containerColor,
            shadowElevation = if (isFloatingPill) 6.dp else 3.dp,
            border = if (isFloatingPill) {
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFloatingPill) {
                            Modifier
                        } else {
                            // 顺序：先导航栏后键盘，IME 打开时导航栏 inset 被消费，不会双重垫高
                            Modifier
                                .navigationBarsPadding()
                                .imePadding()
                        },
                    ),
            ) {
                if (!isFloatingPill) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isFloatingPill) Spacing.sm else Spacing.lg,
                            vertical = if (isFloatingPill) Spacing.xs else Spacing.smPlus,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    // 表情按钮：键盘↔面板互斥切换（微信式，输入行始终可见于面板正上方）
                    IconButton(
                        onClick = {
                            if (panelExpanded) {
                                closePanel(showKeyboard = true)
                            } else {
                                // 先清焦再隐藏：部分设备对已聚焦 EditText 直接 hide 不生效，清焦让系统原生收起键盘
                                panelExpanded = true
                                handle.view?.clearFocus()
                                keyboard?.hide()
                            }
                        },
                        modifier = Modifier.size(ButtonSide),
                    ) {
                        Icon(
                            imageVector = if (panelExpanded) Icons.Filled.Keyboard else Icons.Filled.Mood,
                            contentDescription = stringResource(
                                if (panelExpanded) R.string.comment_keyboard else R.string.comment_emoji_panel,
                            ),
                            tint = if (panelExpanded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    // 贴边模式内嵌独立描边胶囊；悬浮模式外层已是胶囊，内部透明融合避免双边框
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = FieldMinHeight)
                            .then(
                                if (isFloatingPill) {
                                    Modifier.padding(horizontal = Spacing.xs)
                                } else {
                                    Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                                        .padding(horizontal = Spacing.md)
                                },
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        EmoteCommentField(
                            draft = draft,
                            onDraftChange = onDraftChange,
                            hint = stringResource(R.string.comment_placeholder),
                            handle = handle,
                            mentionName = mentionName,
                            // 激活输入框即键盘接管：收面板并确保键盘弹出（已聚焦时无 focus 事件也覆盖）
                            onFieldActivated = {
                                panelExpanded = false
                                handle.view?.requestFocus()
                                keyboard?.show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val canSend = draft.isNotBlank()
                    IconButton(
                        onClick = {
                            // 空草稿发送是无效操作（外部会拦截），此时不响触觉
                            if (canSend) {
                                haptic(PixivHaptics.HapticCue.SUCCESS)
                            }
                            onPost()
                        },
                        modifier = Modifier.size(ButtonSide),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.comment_send),
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                        )
                    }
                }
                // 底部停靠表情栏：面板展开时外层自动切为贴边模式并把输入行顶起
                AnimatedVisibility(
                    visible = panelExpanded,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                ) {
                    CommentEmojiPanel(
                        stamps = stamps,
                        onStampPick = { stamp ->
                            onStampPick?.invoke(stamp)
                            panelExpanded = false
                        },
                        onEmojiPick = { tag ->
                            handle.insertEmote(tag)?.let(onDraftChange)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(panelHeight),
                    )
                }
            }
        }
    }
}

/** 本次会话未记录到键盘高度前的首次展开兜底。 */
private val DefaultPanelHeight = 300.dp

private val FieldMinHeight = 48.dp

private val FloatingInputMaxWidth = 560.dp

/** 默认 48dp 触达区偏大，压缩给输入框让位。 */
private val ButtonSide = 40.dp

/**
 * 高度由调用方给定（对齐上次键盘高度），不自带导航栏 padding（外层列统一处理）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentEmojiPanel(
    stamps: List<Stamp>,
    onStampPick: (Stamp) -> Unit,
    onEmojiPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(modifier = modifier) {
            // 以页码为值，选中态跟 Pager 落页，点击反向滚页
            PillSegmentedRow(
                options = listOf(
                    0 to R.string.comment_emoji_title,
                    1 to R.string.comment_stamp_title,
                ),
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                        ) {
                            PIXIV_EMOJI_TAGS.forEach { tag ->
                                PixivEmojiTagChip(tag = tag, onClick = { onEmojiPick(tag) })
                            }
                        }
                    }
                    else -> {
                        if (stamps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.comment_stamp_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 72.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                            ) {
                                items(stamps, key = { it.stamp_id }) { stamp ->
                                    PixivImage(
                                        url = stamp.stamp_url,
                                        contentDescription = stringResource(R.string.comment_stamp_cd),
                                        modifier = Modifier
                                            .size(Sizes.s72)
                                            .clip(AppShapes.small)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
                                            .clickable { onStampPick(stamp) },
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}
