package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.actions.LocalFavoriteActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.resolveAccent

data class CreatorProfile(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val covers: List<String> = emptyList(),
    val isFollowed: Boolean = false,
)

@Composable
fun CreatorProfileCard(
    profile: CreatorProfile,
    onToggleFollow: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 按用户 ID 去重订阅全局关注覆盖态，避免其他画师变动触发整列重组并同步失败回滚
    val favoriteActions = LocalFavoriteActions.current
    val followedOverride by if (favoriteActions != null) {
        remember(favoriteActions, profile.id) {
            favoriteActions.followedUsers
                .map { it[profile.id] }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = favoriteActions.followedUsers.value[profile.id])
    } else {
        remember { mutableStateOf<Boolean?>(null) }
    }
    var localFollowed by remember(profile.id, profile.isFollowed) { mutableStateOf(profile.isFollowed) }
    val followed = followedOverride ?: localFollowed

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            profile.covers.take(3).forEach { url ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.mdPlus, end = Spacing.mdPlus, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.offset(y = (-24).dp)) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Sizes.s64)
                        .clip(CircleShape)
                        .border(2.dp, resolveAccent(SettingAccentViolet), CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = {
                    val next = !followed
                    if (favoriteActions == null) {
                        localFollowed = next
                    }
                    onToggleFollow(next)
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.height(40.dp),
            ) {
                Text(if (followed) stringResource(R.string.following) else stringResource(R.string.follow))
            }
        }
    }
}
