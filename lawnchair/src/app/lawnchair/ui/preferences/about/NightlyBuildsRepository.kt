package app.lawnchair.ui.preferences.about

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import app.lawnchair.ui.preferences.components.hasInstallPermission
import app.lawnchair.ui.preferences.components.requestInstallPermission
import com.android.launcher3.BuildConfig
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NightlyBuildsRepository private constructor(
    private val applicationContext: Context,
) : SafeCloseable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val api = RetrofitClient.githubService

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
                    // Update progress on the main thread if UI is involved
                    // For now, updating directly as _updateState is thread-safe (StateFlow)
                    _updateState.update { UpdateState.Downloading(progress) }
                }
                if (file != null) {
                    _updateState.update { UpdateState.Downloaded(file) }
                } else {
                    // It's better to log and set to Failed state than to throw an exception
                    // that might not be handled by the caller of this repository method.
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
        if (!hasInstallPermission(applicationContext as Application)) { // Assuming context is Application
            requestInstallPermission(applicationContext)
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
            val apkDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Lawnchair",
            )
            if (!apkDir.exists()) apkDir.mkdirs()
            val apkFile = File(apkDir, "Lawnchair-update.apk")

            val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
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
                        // Ensure onProgress is called from a scope that can update UI if needed
                        // For this repository, we can call it directly or ensure the passed
                        // coroutineScope is appropriate.
                        // Using withContext to switch to a scope that can safely update _updateState
                        // if onProgress directly updates it from a different thread.
                        // However, since onProgress is a lambda passed from downloadUpdate (which uses coroutineScope),
                        // it should be fine. For safety, or if onProgress were more complex:
                        withContext(coroutineScope.coroutineContext) {
                            onProgress(bytesDownloaded / totalBytes)
                        }
                    }
                }
            }
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "APK Download failed", e)
            null
        }
    }

    override fun close() {}

    companion object {
        private const val TAG = "NightlyBuildsRepository"

        @JvmField
        val INSTANCE = MainThreadInitializedObject { NightlyBuildsRepository(it) }

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}
