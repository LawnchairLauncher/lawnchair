package app.lawnchair.ui.preferences.components

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.lawnchair.api.gh.api
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun CheckUpdate(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var latestDownloadUrl by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    // As of now the version string looks like this (CI builds only):
    // <major>.<branch>.(#<CI build number>)
    // This is done inside build.gradle in the source root. Reflect
    // changes from there if needed.
    val currentVersionNumber = BuildConfig.VERSION_DISPLAY_NAME
        .substringAfterLast("#")
        .removeSuffix(")")
        .toIntOrNull() ?: 0

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val releases = api.getReleases()
                releases.forEach { release ->
                    if (release.tag_name == "nightly") {
                        release.assets.forEach { asset ->
                            val releaseNumber = asset.name
                                .substringAfter("_")
                                .substringBefore("-")
                                .toIntOrNull() ?: 0
                            if (releaseNumber > currentVersionNumber) {
                                latestDownloadUrl = asset.browser_download_url
                                updateAvailable = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OTA", "Error fetching latest nightly release", e)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (updateAvailable) {
            latestDownloadUrl?.let { url ->
                if (!isDownloading && downloadedFile == null) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isDownloading = true
                                downloadedFile = downloadApk(url) { progress -> downloadProgress = progress }
                                isDownloading = false
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.download_update))
                    }
                }

                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                downloadedFile?.let { file ->
                    Button(
                        onClick = {
                            if (!hasInstallPermission(context)) {
                                requestInstallPermission(context)
                                return@Button
                            }
                            val apkUri = FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.install_update))
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.pro_updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

fun hasInstallPermission(context: Context): Boolean {
    return if (Utilities.ATLEAST_O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

fun requestInstallPermission(activity: Context) {
    if (Utilities.ATLEAST_O) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${activity.packageName}".toUri(),
        )
        activity.startActivity(intent)
    }
}

suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
    try {
        val apkFile = File(Environment.getExternalStorageDirectory(), "Lawnchair/update.apk").apply { parentFile?.mkdirs() }
        val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
        val body = response.body ?: return@withContext null

        val totalBytes = body.contentLength().toFloat()
        if (totalBytes <= 0) return@withContext null

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
        Log.e("OTA", "Download failed", e)
        null
    }
}
