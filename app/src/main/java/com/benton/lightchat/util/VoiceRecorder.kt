package com.benton.lightchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): Boolean {
        cancel()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(64_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            output = file
            true
        } catch (error: Exception) {
            runCatching { mediaRecorder.release() }
            file.delete()
            recorder = null
            output = null
            false
        }
    }

    fun stop(durationMs: Long): File? {
        val mediaRecorder = recorder ?: return null
        val file = output
        recorder = null
        output = null
        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            if (file == null || !file.exists() || file.length() <= 0) {
                file?.delete()
                null
            } else {
                val seconds = (durationMs / 1000).toInt().coerceAtLeast(1)
                val renamed = File(file.parentFile, "voice_${seconds}s.m4a")
                if (file.renameTo(renamed)) renamed else file
            }
        } catch (error: Exception) {
            runCatching { mediaRecorder.release() }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val mediaRecorder = recorder
        val file = output
        recorder = null
        output = null
        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.release() }
        }
        file?.delete()
    }
}
