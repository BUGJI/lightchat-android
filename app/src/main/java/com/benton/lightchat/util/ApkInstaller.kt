package com.benton.lightchat.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

object ApkInstaller {

    sealed interface Result {
        data object Launched : Result
        data object NeedPermission : Result
        data class Error(val message: String) : Result
    }

    fun install(context: Context, apk: File): Result {
        if (!apk.exists()) {
            return Result.Error("安装包已不存在，请重新下载")
        }
        val archive = try {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        } catch (error: Exception) {
            null
        }
        if (archive?.packageName != context.packageName) {
            return Result.Error("安装包校验失败")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return Result.NeedPermission
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.Launched
        } catch (error: ActivityNotFoundException) {
            Result.Error("未找到系统安装器")
        } catch (error: Exception) {
            Result.Error(error.message ?: "无法启动安装")
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            val fallback = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallback)
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}
