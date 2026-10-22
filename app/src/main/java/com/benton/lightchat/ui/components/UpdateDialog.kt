package com.benton.lightchat.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benton.lightchat.ui.profile.UpdateViewModel
import com.benton.lightchat.util.ApkInstaller
import java.io.File

@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val release = state.release

    LaunchedEffect(state.downloadedApk) {
        val apk = state.downloadedApk ?: return@LaunchedEffect
        launchInstaller(context, apk, viewModel)
    }

    if (!state.showDialog || release == null) return

    val notes = release.notes.trim().ifBlank { "暂无更新说明" }
    val title = when {
        state.needsInstallPermission -> "允许安装应用"
        state.downloadError != null && state.downloadedApk != null -> "无法安装"
        state.downloadError != null -> "下载失败"
        state.downloading -> "正在下载更新"
        state.downloadedApk != null -> "安装更新"
        else -> "发现新版本 v${release.version}"
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.downloading) viewModel.dismissDialog()
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.downloading -> {
                        Text(
                            text = "正在下载 v${release.version}，请保持网络连接。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.progress == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { (state.progress ?: 0) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${state.progress}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.needsInstallPermission -> {
                        Text(
                            text = "需要允许 LightChat 安装未知来源应用，才能完成更新。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "授权后返回应用，点击“立即安装”即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.downloadError != null -> {
                        Text(
                            text = state.downloadError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> {
                        Text(
                            text = "当前版本 ${state.currentVersion} → 新版本 ${release.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                        if (release.apkUrl == null) {
                            Text(
                                text = "该版本未提供应用内安装包，请前往 GitHub 下载。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.downloading -> {
                    TextButton(onClick = viewModel::cancelDownload) { Text("取消下载") }
                }

                state.needsInstallPermission -> {
                    TextButton(
                        onClick = { ApkInstaller.openInstallPermissionSettings(context) },
                    ) { Text("去授权") }
                }

                state.downloadError != null -> {
                    val apk = state.downloadedApk
                    if (apk != null) {
                        TextButton(onClick = { launchInstaller(context, apk, viewModel) }) {
                            Text("重试安装")
                        }
                    } else {
                        TextButton(onClick = viewModel::downloadUpdate) { Text("重试") }
                    }
                }

                else -> {
                    TextButton(
                        onClick = viewModel::downloadUpdate,
                        enabled = release.apkUrl != null,
                    ) { Text("应用内下载") }
                }
            }
        },
        dismissButton = {
            if (!state.downloading) {
                Row {
                    if (state.needsInstallPermission) {
                        TextButton(
                            onClick = {
                                val apk = state.downloadedApk
                                if (apk != null) launchInstaller(context, apk, viewModel)
                            },
                        ) { Text("立即安装") }
                    }
                    TextButton(onClick = { openUrl(context, release.releaseUrl) }) {
                        Text(if (state.needsInstallPermission) "GitHub" else "前往 GitHub")
                    }
                    TextButton(onClick = viewModel::dismissDialog) { Text("稍后") }
                }
            }
        },
    )
}

private fun launchInstaller(context: Context, apk: File, viewModel: UpdateViewModel) {
    when (val result = ApkInstaller.install(context, apk)) {
        ApkInstaller.Result.Launched -> viewModel.onInstallLaunched()
        ApkInstaller.Result.NeedPermission -> viewModel.onInstallPermissionRequired()
        is ApkInstaller.Result.Error -> viewModel.onInstallError(result.message)
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
    } catch (_: Exception) {
    }
}
