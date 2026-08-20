package com.pixiv.reader.feature.comments.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor

/**
 * 评论列表加载骨架：仿评论行布局（36dp 圆头像 + 昵称/时间条 + 正文 2 行 + 分隔线）
 * 渲染 8 条，呼吸脉冲替代全屏转圈。
 */
@Composable
internal fun CommentSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "commentSkeleton")
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(count = 8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SkeletonBlock(Modifier.size(36.dp).clip(CircleShape), color)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        Spacer(Modifier.weight(1f))
                        SkeletonBlock(
                            modifier = Modifier.width(48.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                    }
                    SkeletonBlock(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.65f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
