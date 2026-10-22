package com.benton.lightchat

import android.app.Application
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.remote.ApiClient
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.TokenStore
import com.benton.lightchat.data.repository.AuthRepository
import com.benton.lightchat.data.repository.ChatRepository
import com.benton.lightchat.data.repository.FriendRepository
import com.benton.lightchat.data.repository.ServerRepository
import com.benton.lightchat.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(application)
    val tokenStore = TokenStore()
    val apiClient = ApiClient(tokenStore)
    private val executor = ApiExecutor(apiClient, tokenStore, preferences)

    val authRepository = AuthRepository(executor, preferences, tokenStore)
    val chatRepository = ChatRepository(application, executor)
    val userRepository = UserRepository(executor)
    val friendRepository = FriendRepository(executor)
    val serverRepository = ServerRepository(apiClient, executor)

    init {
        scope.launch {
            preferences.stateFlow.collect { state ->
                tokenStore.token = state.token
                apiClient.setBaseUrl(state.baseUrl)
            }
        }
    }
}

class LightchatApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
