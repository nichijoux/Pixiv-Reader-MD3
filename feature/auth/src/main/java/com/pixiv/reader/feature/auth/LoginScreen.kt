package com.pixiv.reader.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 应用 Logo（对应 Launcher 图标：蓝底白字 P）
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = Color(0xFF0096FA),
                    shape = RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Pixiv Reader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "浏览插画与小说，享受舒适阅读",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
        }

        if (uiState.error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = uiState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismissError) { Text("知道了") }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onLogin,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text("使用 Pixiv 账号登录", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRegister,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text("注册临时账号")
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "登录即代表同意 Pixiv 服务条款。\n若无法打开登录页，请检查网络连接。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
