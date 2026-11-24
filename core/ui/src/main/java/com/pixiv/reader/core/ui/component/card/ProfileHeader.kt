package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.pixiv.api.model.User
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

data class ProfileHeaderData(
    val name: String,
    val account: String? = null,
    val avatarUrl: String? = null,
)

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
        horizontalArrangement = Arrangement.spacedBy(Spacing.mdPlus),
    ) {
        UserAvatar(
            name = profile.name,
            avatarUrl = profile.avatarUrl,
            modifier = Modifier.size(Sizes.s72),
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
                    modifier = Modifier.padding(top = Spacing.xxs),
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

fun ProfileHeaderData(user: User?): ProfileHeaderData = ProfileHeaderData(
    name = user?.name.orEmpty(),
    account = user?.account,
    avatarUrl = user?.profile_image_urls?.best(),
)
