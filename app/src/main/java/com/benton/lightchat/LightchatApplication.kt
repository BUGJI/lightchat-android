package com.benton.lightchat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.benton.lightchat.data.local.AttachmentCache
import com.benton.lightchat.data.local.AttachmentCacheInterceptor
import com.benton.lightchat.data.local.ChatDatabase
import com.benton.lightchat.data.local.ChatLocalStore
import com.benton.lightchat.data.local.StorageManager
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.cache.CacheDatabase
import com.benton.lightchat.data.local.cache.SnapshotStore
import com.benton.lightchat.data.cache.CacheWarmer
import com.benton.lightchat.data.remote.ApiClient
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.TokenStore
import com.benton.lightchat.data.repository.AuthRepository
import com.benton.lightchat.data.repository.ChatRepository
import com.benton.lightchat.data.repository.FriendRepository
import com.benton.lightchat.data.repository.SendQueue
import com.benton.lightchat.data.repository.ServerRepository
import com.benton.lightchat.data.repository.UpdateRepository
import com.benton.lightchat.data.repository.UserRepository
import com.benton.lightchat.data.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(application)
    val tokenStore = TokenStore()
    val apiClient = ApiClient(tokenStore)
    private val executor = ApiExecutor(apiClient, tokenStore, preferences)

    val chatDatabase = ChatDatabase.create(application)
    val chatLocalStore = ChatLocalStore(chatDatabase)
    val cacheDatabase = CacheDatabase.create(application)
    val snapshotStore = SnapshotStore(cacheDatabase)
    val attachmentCache = AttachmentCache(application, apiClient.httpClient)

    val imageLoader: ImageLoader = ImageLoader.Builder(application)
        .components {
            add(AttachmentCacheInterceptor(attachmentCache))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(application.filesDir, "image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .memoryCache {
            MemoryCache.Builder(application)
                .maxSizePercent(0.15)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    val storageManager = StorageManager(
        context = application,
        database = chatDatabase,
        cacheDatabase = cacheDatabase,
        imageLoader = imageLoader,
        attachmentCache = attachmentCache,
    )

    val authRepository = AuthRepository(executor, preferences, tokenStore, snapshotStore)
    val chatRepository = ChatRepository(
        context = application,
        executor = executor,
        preferences = preferences,
        localStore = chatLocalStore,
        attachmentCache = attachmentCache,
        snapshotStore = snapshotStore,
    )
    val sendQueue = SendQueue(
        scope = scope,
        repository = chatRepository,
        localStore = chatLocalStore,
        preferences = preferences,
    )
    val syncCoordinator = SyncCoordinator(
        scope = scope,
        repository = chatRepository,
        localStore = chatLocalStore,
        preferences = preferences,
        sendQueue = sendQueue,
    )
    val userRepository = UserRepository(executor, preferences, snapshotStore)
    val friendRepository = FriendRepository(executor, preferences, snapshotStore)
    val serverRepository = ServerRepository(apiClient, executor, preferences, snapshotStore)
    val cacheWarmer = CacheWarmer(
        scope = scope,
        preferences = preferences,
        snapshotStore = snapshotStore,
        userRepository = userRepository,
        friendRepository = friendRepository,
        chatRepository = chatRepository,
        serverRepository = serverRepository,
    )
    val updateRepository = UpdateRepository(application)

    init {
        scope.launch { preferences.migrateTokenEncryption() }
        scope.launch {
            preferences.stateFlow.collect { state ->
                tokenStore.token = state.token
                apiClient.setBaseUrl(state.baseUrl)
            }
        }
        sendQueue.drain()
        scope.launch {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            listOf(
                File(application.cacheDir, "camera"),
                File(application.cacheDir, "uploads"),
            ).forEach { dir ->
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) file.delete()
                }
            }
            application.cacheDir.listFiles()?.forEach { file ->
                if ((file.name.startsWith("voice_") || file.name.startsWith("upload_")) &&
                    file.lastModified() < cutoff
                ) {
                    file.delete()
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    syncCoordinator.start()
                }

                override fun onStop(owner: LifecycleOwner) {
                    syncCoordinator.stop()
                }
            },
        )
    }
}

class LightchatApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}
