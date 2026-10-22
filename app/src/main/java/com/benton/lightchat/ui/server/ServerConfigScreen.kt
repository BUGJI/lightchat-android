package com.benton.lightchat.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.repository.ServerTestResult
import com.benton.lightchat.util.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    initialUrl: String?,
    allowBack: Boolean,
    clearSessionOnSave: Boolean,
    onSaved: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: ServerConfigViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    val normalized = UrlUtils.normalize(input)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.savedUrl) {
        val saved = state.savedUrl
        if (saved != null) {
            viewModel.consumeSaved()
            onSaved(saved)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (allowBack) "服务器设置" else "连接服务器") },
                navigationIcon = {
                    if (allowBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "配置服务器 API 地址",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "请输入 LightChat 后端地址，必须以 /api 结尾，例如：\nhttp://192.168.1.10/api\n\nAndroid 模拟器访问电脑本机可使用 http://10.0.2.2/api。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    viewModel.clearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 地址") },
                placeholder = { Text("https://example.com/api") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                supportingText = {
                    val support = when {
                        input.isBlank() -> "地址需以 /api 结尾"
                        normalized != null -> "将使用：$normalized"
                        else -> "地址格式不正确，需以 /api 结尾"
                    }
                    Text(support)
                },
                isError = input.isNotBlank() && normalized == null,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.test(input)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.testing && !state.saving && input.isNotBlank(),
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.testing) "测试中…" else "测试连接")
                }
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.save(input, clearSessionOnSave)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = normalized != null && !state.saving && !state.testing,
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.saving) "保存中…" else "保存并继续")
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.testResult?.let { result ->
                TestResultCard(result)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "提示：服务端需允许来自 App 的网络访问。当前客户端为便于局域网调试，已允许 HTTP 明文连接。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TestResultCard(result: ServerTestResult) {
    val ok = result.reachable && result.allOk
    val containerColor = when {
        ok -> MaterialTheme.colorScheme.secondaryContainer
        result.reachable -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !result.reachable -> "无法连接"
                        result.allOk -> "连接成功"
                        else -> "服务器可达，但环境检查未通过"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(result.message, style = MaterialTheme.typography.bodyMedium)
            if (result.reachable) {
                Text(
                    "耗时 ${result.latencyMs} ms" + (result.phpVersion?.let { " · PHP $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.normalizedUrl?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
