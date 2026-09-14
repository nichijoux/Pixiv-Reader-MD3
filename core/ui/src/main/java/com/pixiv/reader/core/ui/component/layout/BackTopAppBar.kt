package com.pixiv.reader.core.ui.component.layout

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.ui.R

/**
 * 返回式顶栏（Scaffold `topBar` 槽用）：标题 + 返回键 + 可选右侧动作槽，surface 底色。
 *
 * 收敛各全屏页逐字重复的 `TopAppBar(title + ArrowBack navigationIcon + topAppBarColors)`
 * 骨架；需要平板限宽居中标题等特殊形态的页面仍可自写 TopAppBar。
 *
 * @param title 标题文案（调用方 `stringResource` 解析）
 * @param onBack 返回回调
 * @param modifier 栏 Modifier
 * @param actions 右侧动作槽（如日期筛选入口、菜单）；默认无
 * @return 无返回值
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.core_cd_back),
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
