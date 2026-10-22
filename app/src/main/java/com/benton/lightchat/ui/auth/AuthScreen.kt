package com.benton.lightchat.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.ui.server.ServerConfigScreen

@Composable
fun AuthScreen(
    serverUrl: String,
    viewModel: AuthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showServerConfig by rememberSaveable { mutableStateOf(false) }
    var showForgotPassword by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    if (showForgotPassword) {
        ForgotPasswordScreen(onBack = { showForgotPassword = false })
        return
    }

    if (showServerConfig) {
        ServerConfigScreen(
            initialUrl = serverUrl,
            allowBack = true,
            clearSessionOnSave = false,
            onSaved = { showServerConfig = false },
            onBack = { showServerConfig = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = serverUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showServerConfig = true }) {
                Text("更换服务器")
            }
        }

        Spacer(Modifier.height(36.dp))
        Icon(
            imageVector = Icons.Filled.Forum,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "LightChat",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "轻量、简单的即时聊天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))
        TabRow(selectedTabIndex = if (state.mode == AuthMode.Login) 0 else 1) {
            Tab(
                selected = state.mode == AuthMode.Login,
                onClick = { viewModel.switchMode(AuthMode.Login) },
                text = { Text("登录") },
            )
            Tab(
                selected = state.mode == AuthMode.Register,
                onClick = { viewModel.switchMode(AuthMode.Register) },
                text = { Text("注册") },
            )
        }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::setUsername,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (state.mode == AuthMode.Login) "用户名或邮箱" else "用户名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        if (state.mode == AuthMode.Register) {
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
            ),
            supportingText = {
                if (state.mode == AuthMode.Register) Text("至少 6 位")
            },
        )

        if (state.mode == AuthMode.Login) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setRememberUsername(!state.rememberUsername) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.rememberUsername,
                        onCheckedChange = viewModel::setRememberUsername,
                        enabled = !state.loading,
                    )
                    Text("记住用户名", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = state.rememberUsername) {
                            viewModel.setRememberPassword(!state.rememberPassword)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.rememberPassword,
                        onCheckedChange = viewModel::setRememberPassword,
                        enabled = !state.loading && state.rememberUsername,
                    )
                    Text(
                        text = "记住密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.rememberUsername) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        state.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.submit()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = if (state.loading) {
                    "请稍候…"
                } else if (state.mode == AuthMode.Login) {
                    "登录"
                } else {
                    "注册并登录"
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                viewModel.switchMode(
                    if (state.mode == AuthMode.Login) AuthMode.Register else AuthMode.Login,
                )
            },
            enabled = !state.loading,
        ) {
            Text(
                if (state.mode == AuthMode.Login) {
                    "还没有账号？立即注册"
                } else {
                    "已有账号？返回登录"
                },
            )
        }

        if (state.mode == AuthMode.Login) {
            TextButton(
                onClick = { showForgotPassword = true },
                enabled = !state.loading,
            ) {
                Text("忘记密码？")
            }
        }
    }
}
