package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.network.update.AppRelease
import com.pixiv.reader.core.ui.component.text.ChangelogMarkdownText

/**
 * 新版本更新对话框：标题（含版本号）+ changelog 正文（markdown 渲染，超长可滚动）
 * + 前往下载 / 下次再说。
 *
 * 项目通用 [ConfirmDialog] 封装（WARNING=primary 强调）；「我的」页手动检测更新与
 * 启动自动检查共用，保证两处弹层一致。文案经参数注入（更新相关字符串归「我的」页模块）。
 *
 * @param release GitHub Release 快照（tagName 供标题拼接、body 作 changelog 正文）
 * @param title 对话框标题（调用方用 stringResource 格式化，如「发现新版本 v1.2.3」）
 * @param confirmText 确认按钮文字（如「前往下载」；具体跳转动作由 [onConfirm] 决定）
 * @param dismissText 取消按钮文字（如「下次再说」）
 * @param onConfirm 确认回调（调用方自行打开 Release 页并关闭对话框）
 * @param onDismiss 关闭回调（点取消 / 点对话框外 / 按返回键）
 * @return 无返回值（组合式 UI，无返回）
 */
@Composable
fun UpdateReleaseDialog(
    release: AppRelease,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        variant = ConfirmDialogVariant.WARNING,
        dismissText = dismissText,
        bodyContent = {
            // changelog 为空（Release 未写 body）时不出正文区，仅标题 + 按钮
            if (release.body.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // markdown 渲染（标题/列表/粗体/行内代码/链接），见 ChangelogMarkdownText
                    ChangelogMarkdownText(release.body)
                }
            }
        },
    )
}
