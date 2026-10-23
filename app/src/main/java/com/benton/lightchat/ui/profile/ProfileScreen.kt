package com.benton.lightchat.ui.profile

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.ui.components.FormSkeleton
import com.benton.lightchat.ui.components.InlineSyncBar

@Composable
fun ProfileScreen(
    baseUrl: String?,
    onEditProfile: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    if (state.loading && state.user == null) {
        FormSkeleton(blocks = 2)
        return
    }

    val user = state.user
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (user == null) {
                Text(
                    text = state.error ?: "无法加载个人资料",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = viewModel::refresh) { Text("重试") }
                return@Column
            }

            ProfileHeader(
                user = user,
                baseUrl = baseUrl,
                onEdit = { onEditProfile(user.avatar) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            if (!user.bio.isNullOrBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("个人简介", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(user.bio, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    user.email?.takeIf { it.isNotBlank() }?.let {
                        ProfileInfoRow("邮箱", it, verified = user.emailVerified)
                    }
                    user.joinDate?.takeIf { it.isNotBlank() }?.let {
                        ProfileInfoRow("加入时间", it)
                    }
                    ProfileInfoRow("用户 ID", user.id.toString())
                    state.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                ProfileActionRow(
                    icon = Icons.Filled.Settings,
                    title = "设置",
                    onClick = onOpenSettings,
                )
                ProfileActionRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    onClick = { confirmLogout = true },
                    destructive = true,
                    showDivider = true,
                )
            }
        }

        InlineSyncBar(
            visible = state.refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    },
                    enabled = !state.loggingOut,
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }
}
