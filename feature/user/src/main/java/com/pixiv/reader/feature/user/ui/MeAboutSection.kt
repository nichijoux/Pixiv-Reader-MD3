package com.pixiv.reader.feature.user.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.input.SettingsCard
import com.pixiv.reader.core.ui.component.input.SettingsCardItem
import com.pixiv.reader.feature.user.R

/** 开源仓库地址（与 git remote / CI 发布仓库一致）。 */
private const val OPEN_SOURCE_URL = "https://github.com/nichijoux/Pixiv-Reader-MD3"

/** 仓库链接展示文本（去协议头，副标题更紧凑）。 */
private val OPEN_SOURCE_URL_DISPLAY = OPEN_SOURCE_URL.removePrefix("https://")

/** 开源许可（GPL-2.0）文本直链。 */
private const val OPEN_SOURCE_LICENSE_URL =
    "https://github.com/nichijoux/Pixiv-Reader-MD3/blob/main/LICENSE"

/** 应用图标 Drawable → Bitmap（自适应图标在给定画布上绘制图层）。 */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) return drawable.bitmap
    val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

/** 我的页「关于」：应用信息 / 开源仓库 / 开源许可 / 检查更新。 */
@Composable
internal fun MeAboutSection(
    versionName: String,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    // 应用真实启动器图标（feature 模块访问不到 app 资源，运行时经 PackageManager 获取）
    val appIcon = remember {
        drawableToBitmap(context.packageManager.getApplicationIcon(context.packageName))
    }
    MeSettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Pixiv Reader",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.me_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.me_about_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    CardSpacer()
    // 开源仓库
    SettingsCard(
        SettingsCardItem(
            icon = Icons.Filled.OpenInNew,
            title = stringResource(R.string.me_open_source_repo),
            description = OPEN_SOURCE_URL_DISPLAY,
            trailingIcon = Icons.Filled.OpenInNew,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_SOURCE_URL)))
                }
            },
        ),
    )
    CardSpacer()
    // 开源许可（独立入口，与开源仓库同款按钮；描述说明放在标题下方）
    SettingsCard(
        SettingsCardItem(
            icon = Icons.Filled.Code,
            title = stringResource(R.string.me_open_source_license),
            description = stringResource(R.string.me_open_source_license_desc),
            trailingIcon = Icons.Filled.OpenInNew,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_SOURCE_LICENSE_URL)))
                }
            },
        ),
    )
    CardSpacer()
    // 检查更新
    SettingsCard(
        SettingsCardItem(
            icon = Icons.Filled.SystemUpdate,
            title = stringResource(R.string.me_check_update),
            onClick = onCheckUpdate,
        ),
    )
}
