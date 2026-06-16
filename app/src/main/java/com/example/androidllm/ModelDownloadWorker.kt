package com.example.androidllm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

/**
 * WorkManager worker that downloads a GGUF model to the app's private files directory.
 *
 * The URL is passed as input data. For large models (several GB) prefer a direct
 * download via browser / ADB / adb push and place the file at [TARGET_FILE].
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_TARGET_NAME = "target_name"
        const val TARGET_FILE = "gemma-4b-it.gguf"
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_MODEL_URL)
        val targetName = inputData.getString(KEY_TARGET_NAME) ?: TARGET_FILE
        if (url.isNullOrBlank()) {
            return Result.failure()
        }

        val targetFile = File(applicationContext.filesDir, targetName)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading $targetName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(targetFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        return Result.success()
    }
}
