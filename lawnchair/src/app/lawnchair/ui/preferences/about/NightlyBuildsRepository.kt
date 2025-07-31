package app.lawnchair.ui.preferences.about

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NightlyBuildsRepository private constructor(
    private val applicationContext: Context,
    private val okHttpClient: OkHttpClient,
    private val api: GitHubService,
) : SafeCloseable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
    val updateState = _updateState.asStateFlow()

    fun checkForUpdate() {
        coroutineScope.launch(Dispatchers.IO) {
            _updateState.update { UpdateState.Checking }
            try {
                val releases = api.getReleases()
                val nightly = releases.firstOrNull { it.tagName == "nightly" }
                val asset = nightly?.assets?.firstOrNull()

                val currentVersion = BuildConfig.VERSION_CODE
                val latestVersion =
                    asset?.name?.substringAfter("_")?.substringBefore("-")?.toIntOrNull() ?: 0

                withContext(Dispatchers.Main) {
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
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during update check", e)
                _updateState.update { UpdateState.Failed }
            } catch (e: Exception) { // General fallback
                Log.e(TAG, "Failed to check for update", e)
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

    private fun downloadApk(url: String, onProgress: (Float) -> Unit): File? {
        return try {
            val cacheDir = applicationContext.cacheDir
            val apkDir = File(cacheDir, "updates")
            if (!apkDir.exists()) {
                apkDir.mkdirs()
            }
            val apkFile = File(apkDir, "Lawnchair-update.apk")

            if (apkFile.exists()) {
                apkFile.delete()
            }

            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body
            val totalBytes = body.contentLength().toFloat()
            if (totalBytes <= 0) {
                Log.w(TAG, "Content length is invalid: $totalBytes")
                return null
            }

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
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
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    override fun close() {
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "NightlyBuildsRepository"

        @JvmField
        val INSTANCE = MainThreadInitializedObject {
            NightlyBuildsRepository(it, OkHttpClient(), RetrofitClient.githubService)
        }

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
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
