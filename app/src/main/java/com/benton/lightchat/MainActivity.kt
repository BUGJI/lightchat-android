package com.benton.lightchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.local.PrefsState
import com.benton.lightchat.ui.auth.AuthScreen
import com.benton.lightchat.ui.components.UpdateDialogHost
import com.benton.lightchat.ui.navigation.MainNavHost
import com.benton.lightchat.ui.profile.UpdateViewModel
import com.benton.lightchat.ui.server.ServerConfigScreen
import com.benton.lightchat.ui.theme.LightchatTheme
import com.benton.lightchat.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LightchatApplication).container
        setContent {
            val prefs by container.preferences.stateFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val updateViewModel: UpdateViewModel = viewModel()
            LaunchedEffect(Unit) { updateViewModel.maybeAutoCheck() }
            val themeMode = ThemeMode.fromValue(prefs?.themeMode)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LightchatTheme(
                darkTheme = darkTheme,
                dynamicColor = prefs?.dynamicColor ?: true,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LightchatApp(
                            container = container,
                            state = prefs,
                            updateViewModel = updateViewModel,
                        )
                        UpdateDialogHost(updateViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun LightchatApp(
    container: AppContainer,
    state: PrefsState?,
    updateViewModel: UpdateViewModel,
) {
    if (state == null) {
        SplashScreen()
        return
    }

    val baseUrl = state.baseUrl?.takeIf { it.isNotBlank() }
    when {
        baseUrl == null -> ServerConfigScreen(
            initialUrl = null,
            allowBack = false,
            clearSessionOnSave = false,
            onSaved = {},
        )

        state.token.isNullOrBlank() -> AuthScreen(serverUrl = baseUrl)

        else -> MainNavHost(
            container = container,
            prefs = state,
            updateViewModel = updateViewModel,
        )
    }
}

@Composable
private fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "LightChat",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
