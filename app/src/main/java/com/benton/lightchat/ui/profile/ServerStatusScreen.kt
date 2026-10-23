package com.benton.lightchat.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Card
import com.benton.lightchat.ui.components.FormSkeleton
import com.benton.lightchat.ui.components.SyncBarContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.remote.ServerStatusData
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatusScreen(
    onBack: () -> Unit,
    viewModel: ServerStatusViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器状态") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            FormSkeleton(Modifier.padding(padding), blocks = 2)
            return@Scaffold
        }
        SyncBarContent(
            visible = state.refreshing,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            columnModifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("运行状态", style = MaterialTheme.typography.titleMedium)
                    }
                    when {
                        state.status == null -> {
                            Text(
                                text = state.error ?: "无法获取服务器状态",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = viewModel::load) { Text("重试") }
                        }

                        else -> ServerStatusContent(state.status!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerStatusContent(status: ServerStatusData) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusRow("PHP 版本", status.php.version.ifBlank { "-" })
        StatusRow("内存限制", status.php.memoryLimit.ifBlank { "-" })
        StatusRow(
            "上传限制",
            listOf(status.php.uploadMaxFilesize, status.php.postMaxSize)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
                .ifBlank { "-" },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        StatusRow(
            "磁盘可用",
            if (status.disk.freeMb >= 0) "${formatMb(status.disk.freeMb)} / ${formatMb(status.disk.totalMb)}" else "未知",
        )
        StatusRow("应用占用", formatMb(status.disk.appUsedMb))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        StatusRow(
            "本月流量",
            "${status.usage.networkFlowMb} MB · ${status.usage.totalRequests} 次请求",
        )
        StatusRow(
            "活跃会话",
            if (status.usage.activeSessions >= 0) status.usage.activeSessions.toString() else "未知",
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatMb(value: Double): String = when {
    value >= 1024 -> String.format(Locale.getDefault(), "%.1f GB", value / 1024)
    else -> String.format(Locale.getDefault(), "%.1f MB", value)
}
