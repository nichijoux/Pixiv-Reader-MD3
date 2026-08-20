package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.User
import com.pixiv.reader.core.ui.R

/** 个人中心数据（可复用：我的页 / 用户主页头部）。 */
data class ProfileHeaderData(
    val name: String,
    val account: String? = null,
    val avatarUrl: String? = null,
)

/**
 * 个人中心头部（Material 主题自适应）。
 *
 * ## UI 设计方式
 * 横向 `Row`：72dp 圆形头像（可点击）+ 文本列（名称 `titleLarge` 加粗 + `@account` 次级色，
 * `weight(1f)` 占满）+ 可选操作按钮（`OutlinedButton`，如"退出登录"）。
 *
 * ## 交互
 * 点击头像/名称 → [onClickProfile]（打开用户主页）；[actionLabel]/[onAction] 同时非空时
 * 显示操作按钮（如退出登录）。
 *
 * @param profile 头部数据（名称/@account/头像 URL）
 * @param onClickProfile 点击头像或名称（打开用户主页）
 * @param modifier 外部传入的 Modifier
 * @param actionLabel 操作按钮文案；null 不显示按钮
 * @param onAction 操作按钮点击回调；null 不显示按钮
 */
@Composable
fun ProfileHeader(
    profile: ProfileHeaderData,
    onClickProfile: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UserAvatar(
            name = profile.name,
            avatarUrl = profile.avatarUrl,
            modifier = Modifier.size(72.dp),
            onClick = onClickProfile,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { stringResource(R.string.not_logged_in) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!profile.account.isNullOrBlank()) {
                Text(
                    text = "@${profile.account}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** 便捷构造：由 User 模型生成 ProfileHeaderData。 */
fun ProfileHeaderData(user: User?): ProfileHeaderData = ProfileHeaderData(
    name = user?.name.orEmpty(),
    account = user?.account,
    avatarUrl = user?.profile_image_urls?.best(),
)
