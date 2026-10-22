package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.LatestRelease
import com.benton.lightchat.util.VersionUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    ERROR,
}

data class UpdateUiState(
    val currentVersion: String = "",
    val autoCheck: Boolean = true,
    val status: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val release: LatestRelease? = null,
    val message: String? = null,
    val showDialog: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val downloadedApk: File? = null,
    val downloadError: String? = null,
    val needsInstallPermission: Boolean = false,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val repository = container.updateRepository

    private val _state = MutableStateFlow(UpdateUiState(currentVersion = currentVersionName()))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var autoCheckStarted = false

    init {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            _state.update { it.copy(autoCheck = prefs.autoCheckUpdates) }
        }
    }

    fun maybeAutoCheck() {
        if (autoCheckStarted) return
        autoCheckStarted = true
        viewModelScope.launch {
            val prefs = container.preferences.current()
            _state.update { it.copy(autoCheck = prefs.autoCheckUpdates) }
            if (!prefs.autoCheckUpdates) return@launch
            val elapsed = System.currentTimeMillis() - prefs.lastUpdateCheckAt
            if (prefs.lastUpdateCheckAt > 0 && elapsed >= 0 && elapsed < AUTO_CHECK_INTERVAL_MS) {
                return@launch
            }
            performCheck(auto = true)
        }
    }

    fun checkNow() {
        if (_state.value.status == UpdateCheckStatus.CHECKING || _state.value.downloading) return
        viewModelScope.launch { performCheck(auto = false) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        if (_state.value.autoCheck == enabled) return
        _state.update { it.copy(autoCheck = enabled) }
        viewModelScope.launch { container.preferences.setAutoCheckUpdates(enabled) }
    }

    fun showUpdateDialog() {
        if (_state.value.release == null) return
        _state.update { it.copy(showDialog = true) }
    }

    fun dismissDialog() {
        if (_state.value.downloading) return
        _state.update {
            it.copy(
                showDialog = false,
                downloadedApk = null,
                downloadError = null,
                needsInstallPermission = false,
            )
        }
    }

    fun downloadUpdate() {
        val release = _state.value.release ?: return
        if (_state.value.downloading) return
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    showDialog = true,
                    downloading = true,
                    progress = null,
                    downloadedApk = null,
                    downloadError = null,
                    needsInstallPermission = false,
                )
            }
            val result = repository.download(release) { progress ->
                _state.update { it.copy(progress = progress) }
            }
            when (result) {
                is ApiResult.Success -> {
                    val file = result.data
                    if (file == null) {
                        _state.update {
                            it.copy(
                                downloading = false,
                                progress = null,
                                downloadError = "下载失败：文件为空",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                downloading = false,
                                progress = 100,
                                downloadedApk = file,
                            )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            downloading = false,
                            progress = null,
                            downloadError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        repository.cancelDownload()
        downloadJob?.cancel()
        downloadJob = null
        _state.update {
            it.copy(
                downloading = false,
                progress = null,
                downloadError = null,
            )
        }
    }

    fun onInstallPermissionRequired() {
        _state.update {
            it.copy(
                needsInstallPermission = true,
                downloadError = null,
            )
        }
    }

    fun onInstallLaunched() {
        _state.update {
            it.copy(
                showDialog = false,
                downloadedApk = null,
                needsInstallPermission = false,
                downloadError = null,
                progress = null,
            )
        }
    }

    fun onInstallError(message: String) {
        _state.update {
            it.copy(
                needsInstallPermission = false,
                downloadError = message,
            )
        }
    }

    private suspend fun performCheck(auto: Boolean) {
        _state.update {
            it.copy(
                status = UpdateCheckStatus.CHECKING,
                message = null,
                release = null,
                downloadedApk = null,
                downloadError = null,
                needsInstallPermission = false,
            )
        }
        when (val result = repository.fetchLatest()) {
            is ApiResult.Success -> {
                val release = result.data
                val current = _state.value.currentVersion
                if (release != null && VersionUtils.isNewer(release.version, current)) {
                    val showDialog = !auto || _state.value.autoCheck
                    _state.update {
                        it.copy(
                            status = UpdateCheckStatus.UPDATE_AVAILABLE,
                            release = release,
                            message = "发现新版本 v${release.version}",
                            showDialog = showDialog,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            status = UpdateCheckStatus.UP_TO_DATE,
                            release = null,
                            message = if (auto) null else "已是最新版本",
                            showDialog = false,
                        )
                    }
                }
                if (auto) {
                    container.preferences.setLastUpdateCheckAt(System.currentTimeMillis())
                }
            }

            is ApiResult.Failure -> {
                _state.update {
                    it.copy(
                        status = if (auto) UpdateCheckStatus.IDLE else UpdateCheckStatus.ERROR,
                        release = null,
                        message = if (auto) null else result.message,
                    )
                }
            }
        }
    }

    private fun currentVersionName(): String {
        val app = getApplication<Application>()
        return try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            info.versionName?.takeIf { it.isNotBlank() } ?: "0"
        } catch (_: Exception) {
            "0"
        }
    }

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
