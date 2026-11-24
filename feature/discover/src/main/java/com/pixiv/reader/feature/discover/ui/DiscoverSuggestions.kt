package com.pixiv.reader.feature.discover.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pixiv.api.model.AutocompleteTag
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

private data class IdQuickOpenAction(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
internal fun SuggestionList(
    suggestions: List<AutocompleteTag>,
    numericId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onPick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (numericId != null) {
            item(key = "id_quick_open_header") {
                IdQuickOpenHeader(numericId = numericId)
            }
            item(key = "id_quick_open_cards") {
                val idActions = listOf(
                    IdQuickOpenAction(Icons.AutoMirrored.Filled.MenuBook, R.string.search_type_novel) {
                        onOpenNovel(numericId)
                    },
                    IdQuickOpenAction(Icons.Filled.Image, R.string.search_type_illust) {
                        onOpenIllust(numericId)
                    },
                    IdQuickOpenAction(Icons.Filled.Person, R.string.search_type_user) {
                        onOpenUser(numericId)
                    },
                    IdQuickOpenAction(Icons.Filled.CollectionsBookmark, R.string.search_type_series) {
                        onOpenSeries(numericId)
                    },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                ) {
                    idActions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                        if (rowIndex > 0) Spacer(Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            rowActions.forEach { action ->
                                IdQuickOpenCard(
                                    icon = action.icon,
                                    label = stringResource(action.labelRes),
                                    onClick = action.onClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                item(key = "id_quick_open_divider") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    )
                }
            }
        }
        if (suggestions.isEmpty() && numericId == null) {
            item {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lgPlus),
                )
            }
        }
        items(suggestions, key = { it.name ?: it.hashCode() }) { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { tag.name?.let(onPick) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.s18))
                Text(
                    text = tag.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Spacing.md),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IdQuickOpenHeader(numericId: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Tag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.s18),
        )
        Text(
            text = stringResource(R.string.search_id_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = Spacing.xsPlus)
                .weight(1f),
        )
        Text(
            text = "#$numericId",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.smPlus, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun IdQuickOpenCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "idQuickOpenPress",
    )
    Surface(
        onClick = onClick,
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.s32)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), AppShapes.circle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.s18),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
