package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R as CoreUiR
import com.pixiv.reader.core.ui.component.card.ProfileHeaderData
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.input.pressScale
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.user.R

/**
 * 我的页「账号」区块：用户头像/昵称/@account（整行可点跳转个人主页）+ 退出登录行。
 * 复用 [MeGroupCard] 与 [MeRow] 保持与下方各设置分组完全一致的 Material 3 Expressive 风格
 * （28dp extraLarge 圆角、不透明 surfaceContainer、组内留白分隔无分割线）。
 *
 * @param profile 用户头部展示数据（昵称 / @account / 头像 URL）
 * @param onClickProfile 点击用户资料行跳转个人主页的回调；uid 未就绪时传 null 禁用点击与尾随箭头
 * @param onLogout 点击退出登录行的回调（由上层触发确认弹窗）
 * @return 无返回值
 */
@Composable
internal fun MeAccountSection(
    profile: ProfileHeaderData,
    onClickProfile: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    // 组合期无条件捕获触觉，避免在条件修饰链里调 @Composable
    val haptic = rememberHapticCue()
    val interactionSource = remember { MutableInteractionSource() }
    MeGroupCard {
        // 用户资料行：整行承载点击（UserAvatar 不单独挂 onClick，避免嵌套可点击与双涟漪）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(
                    if (onClickProfile != null) {
                        Modifier
                            .pressScale(interactionSource = interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = {
                                    haptic(PixivHaptics.HapticCue.SELECT)
                                    onClickProfile()
                                },
                            )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                name = profile.name,
                avatarUrl = profile.avatarUrl,
                modifier = Modifier.size(Sizes.s64),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.lg),
            ) {
                Text(
                    text = profile.name.ifBlank { stringResource(CoreUiR.string.not_logged_in) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleText = when {
                    !profile.account.isNullOrBlank() -> "@${profile.account}"
                    onClickProfile != null -> stringResource(R.string.me_account_view_profile)
                    else -> null
                }
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            if (onClickProfile != null) {
                MeArrowTrailing()
            }
        }
        // 退出登录行：与常规设置项保持统一前置徽标与尾随箭头语汇
        MeRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = stringResource(R.string.me_logout),
            subtitle = stringResource(R.string.me_logout_desc),
            accent = SettingAccentOrange,
            trailing = { MeArrowTrailing() },
            onClick = onLogout,
        )
    }
}
