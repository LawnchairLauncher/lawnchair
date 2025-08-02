package app.lawnchair.ui.preferences.about

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities
import java.io.File
import java.io.IOException
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NightlyBuildsRepository(
    val applicationContext: Context,
    val api: GitHubService,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
    val updateState = _updateState.asStateFlow()

    fun checkForUpdate() {
        coroutineScope.launch(Dispatchers.Default) {
            _updateState.update { UpdateState.Checking }
            try {
                val releases = api.getReleases()
                val nightly = releases.firstOrNull { it.tagName == "nightly" }
                val asset = nightly?.assets?.firstOrNull()

                val currentVersion = BuildConfig.VERSION_DISPLAY_NAME
                    .substringAfter("_")
                    .substringBefore("-")
                    .toIntOrNull() ?: 0
                val latestVersion =
                    asset?.name?.substringAfter("_")?.substringBefore("-")?.toIntOrNull() ?: 0

                if (asset != null && latestVersion > currentVersion) {
                    _updateState.update {
                        UpdateState.Available(
                            asset.name,
                            asset.browserDownloadUrl,
                        )
                    }
                } else {
                    _updateState.update { UpdateState.UpToDate }
                }
            } catch (e: Exception) {
                when (e) {
                    is IOException -> {
                        Log.e(TAG, "Network error during update check", e)
                    }
                    else -> {
                        Log.e(TAG, "Failed to check for update", e)
                    }
                }
                _updateState.update { UpdateState.Failed }
            }
        }
    }

    fun downloadUpdate() {
        val currentState = _updateState.value
        if (currentState !is UpdateState.Available) return

        coroutineScope.launch(Dispatchers.IO) {
            _updateState.update { UpdateState.Downloading(0f) }
            try {
                val file = downloadApk(currentState.url) { progress ->
                    _updateState.update { UpdateState.Downloading(progress) }
                }
                if (file != null) {
                    _updateState.update { UpdateState.Downloaded(file) }
                } else {
                    Log.e(TAG, "Downloaded file is null")
                    _updateState.update { UpdateState.Failed }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _updateState.update { UpdateState.Failed }
            }
        }
    }

    fun installUpdate(file: File) {
        if (!applicationContext.hasInstallPermission()) {
            // todo expose proper permission UI instead of requesting immediately on click
            applicationContext.requestInstallPermission()
            return
        }
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    private suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): File? {
        return try {
            val cacheDir = applicationContext.cacheDir
            val apkDirPath = cacheDir.toPath().resolve("updates").createDirectories()
            val apkFilePath = apkDirPath.resolve("Lawnchair-update.apk").apply { deleteIfExists() }

            val responseBody = api.downloadFile(url)
            val totalBytes = responseBody.contentLength().toFloat()
            if (totalBytes <= 0) {
                Log.w(TAG, "Content length is invalid: $totalBytes")
                return null
            }

            responseBody.byteStream().use { input ->
                apkFilePath.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        onProgress(bytesDownloaded / totalBytes)
                    }
                }
            }
            apkFilePath.toFile()
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "NightlyBuildsRepository"
    }
}

private fun Context.hasInstallPermission(): Boolean {
    return if (Utilities.ATLEAST_O) {
        packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

private fun Context.requestInstallPermission() {
    if (Utilities.ATLEAST_O) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:$packageName".toUri(),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
