package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.feature.reader.state.PageElement
import com.pixiv.reader.feature.reader.state.ReaderPage

/** 正文内容内边距（翻页 / 仿真 / 渲染共用）。 */
internal val PAGE_H_PADDING = 24.dp
internal val PAGE_V_PADDING = 16.dp

/**
 * 渲染单页内容（文本行 + 图片混合排版）。
 * 供翻页模式与仿真模式共用（图片高度来自分页器自适应值，图片块含说明文字）。
 */
@Composable
internal fun RenderReaderPage(
    page: ReaderPage,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Column(modifier = modifier) {
        page.elements.forEach { el ->
            when (el) {
                is PageElement.TextLine -> if (el.text.isEmpty()) {
                    // 空行（段落间距）按行高占位
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { el.heightPx.toDp() }),
                    )
                } else {
                    Text(
                        text = el.text,
                        style = el.style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is PageElement.Image -> ReaderImageBlock(
                    url = el.url,
                    caption = el.caption,
                    height = with(density) { el.heightPx.toDp() },
                )
            }
        }
    }
}

/** 插图块：图片 + 可选说明文字。 */
@Composable
internal fun ReaderImageBlock(url: String, caption: String?, height: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixivImage(
            url = url,
            contentDescription = caption,
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentScale = ContentScale.Fit,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
