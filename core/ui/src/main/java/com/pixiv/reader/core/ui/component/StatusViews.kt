package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R

/**
 * 加载中占位：全屏居中 `CircularProgressIndicator`。
 *
 * ## UI 设计方式
 * `Box` + `fillMaxSize` + `contentAlignment.Center`，转圈指示器居中。
 *
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 错误占位：居中显示错误信息 + 重试按钮。
 *
 * ## UI 设计方式
 * `Column` 垂直居中：错误文本（`error` 色，居中）+ "重试" `Button`（带刷新图标）。
 *
 * @param message 错误信息
 * @param onRetry 重试回调（重新发起加载）
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun ErrorBox(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // message 为空/空白时回退到本地化兜底文案（i18n）
    val text = message.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.load_failed)
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.retry), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * 空态占位：居中显示提示文本（次级色）。
 *
 * ## UI 设计方式
 * `Box` + `fillMaxSize` + `contentAlignment.Center`，单行文本居中。
 *
 * @param text 空态提示文案（如"暂无收藏"）
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
