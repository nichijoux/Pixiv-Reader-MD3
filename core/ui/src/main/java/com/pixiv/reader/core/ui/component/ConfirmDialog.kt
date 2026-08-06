package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 确认提示框的视觉种类：决定图标、强调色与确认按钮配色。
 *
 * - [DANGER]：删除 / 清空等危险操作（error 红色系，默认图标删除）。
 * - [WARNING]：提示 / 确认类非破坏操作（primary 蓝色系，默认图标 Info，如退出登录）。
 */
enum class ConfirmDialogVariant(
    val defaultIcon: ImageVector,
) {
    DANGER(Icons.Filled.DeleteOutline),
    WARNING(Icons.Filled.Info),
}

/**
 * 通用确认提示框（项目定制风格，对齐 DESIGN.md 通用组件约定）。
 *
 * ## UI 设计方式
 * 窗口级 [Dialog]（自带 scrim，点外部 / 返回键触发 [onDismiss]）+ 居中卡片：
 * `AppShapes.large` 弹层圆角 + `surfaceContainerHigh` 底 + `tonalElevation` 8dp；
 * 首行「40dp 圆底图标 + 标题」同行（图标作为标题引导，`titleMedium SemiBold`），
 * 说明 `bodyMedium onSurfaceVariant`；
 * 底部按钮行右对齐——取消为 [TextButton]（onSurfaceVariant），确认为**强调色实心按钮**。
 * 强调色按 [variant]：删除类用 `error`（危险操作语义），提示类用 `primary`（如退出登录）。
 * 全部尺寸走 `Spacing` / `AppShapes` Token，无散落 magic number。
 *
 * @param title 确认标题（如「清空搜索历史？」「退出登录？」）
 * @param message 确认说明（删除类通常含「此操作不可撤销」语义）
 * @param confirmText 确认按钮文字（如「清空」「删除」「退出登录」）
 * @param onConfirm 确认回调（执行操作并自行关闭对话框）
 * @param onDismiss 取消/外部点击/返回回调
 * @param modifier 外层 Modifier（默认空）
 * @param variant 视觉种类（默认 [ConfirmDialogVariant.DANGER]，删除/清空类）
 * @param icon 顶部图标；null 时用 [ConfirmDialogVariant.defaultIcon]（默认删除图标）
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ConfirmDialogVariant = ConfirmDialogVariant.DANGER,
    icon: ImageVector? = null,
) {
    // 强调色：删除类 error / 提示类 primary
    val accent = when (variant) {
        ConfirmDialogVariant.DANGER -> MaterialTheme.colorScheme.error
        ConfirmDialogVariant.WARNING -> MaterialTheme.colorScheme.primary
    }
    val onAccent = when (variant) {
        ConfirmDialogVariant.DANGER -> MaterialTheme.colorScheme.onError
        ConfirmDialogVariant.WARNING -> MaterialTheme.colorScheme.onPrimary
    }
    val resolvedIcon = icon ?: variant.defaultIcon
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    // 图标 + 标题同行（图标作为标题引导，不再孤立）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 图标：强调色 8% 圆底 + 强调色图标
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = resolvedIcon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xl),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = onAccent,
                            ),
                        ) {
                            Text(text = confirmText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
