package app.lawnchair.ui.preferences.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.lawnchair.FeedBridge
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProviderInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
)

fun getProviders(context: Context) = FeedBridge.getAvailableProviders(context).map {
    ProviderInfo(
        name = it.loadLabel(context.packageManager).toString(),
        packageName = it.packageName,
        icon = CustomAdaptiveIconDrawable.wrapNonNull(it.loadIcon(context.packageManager)),
    )
}

fun getEntries(context: Context) = getProviders(context).map {
    ListPreferenceEntry(
        value = it.packageName,
        endWidget = {
            if (it.icon != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = it.icon),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(48.dp),
                )
            }
        },
        label = { it.name },
    )
}.toList()

private const val AIDL_BRIDGE_PACKAGE = "amirz.aidlbridge"
private const val AMIRZ_AIDL_PACKAGE = "xyz.amirzaidi.AIDLBridge"
private const val AIDL_BRIDGE_URL = "https://github.com/amirzaidi/AIDLBridge/releases/download/v3/aidlbridge.apk"
private const val TAG = "FeedPreference"

sealed interface AidlBridgeState {
    data object NotInstalled : AidlBridgeState
    data object Installed : AidlBridgeState
    data object Checking : AidlBridgeState
    data class Downloading(val progress: Float) : AidlBridgeState
    data class Downloaded(val file: File) : AidlBridgeState
    data object Failed : AidlBridgeState
}

@Composable
fun FeedPreference(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adapter = preferenceManager().feedProvider.getAdapter()
    val preferredPackage = adapter.state.value
    var entries by remember { mutableStateOf(getEntries(context)) }

    var aidlBridgeState by remember {
        mutableStateOf<AidlBridgeState>(
            if (isAidlBridgeInstalled(context)) AidlBridgeState.Installed else AidlBridgeState.NotInstalled,
        )
    }

    // Check if AIDL Bridge is available as a provider
    val isAidlBridgeAvailable = remember(entries) {
        val providers = getProviders(context)
        providers.any {
            it.packageName == AIDL_BRIDGE_PACKAGE ||
                it.packageName == AMIRZ_AIDL_PACKAGE
        }
    }
    var showReinstallDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var showRestartDialog by remember { mutableStateOf(false) }

    // Listen for package installations
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == AIDL_BRIDGE_PACKAGE && intent.action == Intent.ACTION_PACKAGE_ADDED) {
                    // AIDL Bridge was installed, refresh the entries and update state
                    entries = getEntries(context)
                    aidlBridgeState = AidlBridgeState.Installed

                    // Check if AIDL Bridge appears in the providers list
                    val providers = getProviders(context)
                    if (!providers.any { it.packageName == AIDL_BRIDGE_PACKAGE || it.packageName == AMIRZ_AIDL_PACKAGE }) {
                        // If not, show restart dialog
                        showRestartDialog = true
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }

        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val resolvedEntry = entries.firstOrNull {
        it.value == preferredPackage
    }

    Column(modifier = modifier) {
        ListPreference(
            value = resolvedEntry?.value ?: "",
            onValueChange = adapter::onChange,
            entries = entries,
            label = stringResource(R.string.feed_provider),
            endWidget = resolvedEntry?.endWidget,
        )

        // AIDL Bridge section - Only show if not available as a provider
        if (!isAidlBridgeAvailable) {
            when (aidlBridgeState) {
                is AidlBridgeState.NotInstalled -> {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    AidlBridgeInstallCard(
                        onInstallClick = {
                            scope.launch {
                                downloadAndInstallAidlBridge(context) { state ->
                                    aidlBridgeState = state
                                }
                            }
                        },
                    )
                }
                is AidlBridgeState.Checking -> {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is AidlBridgeState.Downloading -> {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.aidl_bridge_downloading),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            val progress = (aidlBridgeState as AidlBridgeState.Downloading).progress
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                is AidlBridgeState.Downloaded -> {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "AIDL Bridge Ready",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Button(
                                onClick = {
                                    val file = (aidlBridgeState as AidlBridgeState.Downloaded).file
                                    installApk(context, file)
                                    // The BroadcastReceiver will handle state update and refresh
                                },
                            ) {
                                Text(stringResource(R.string.aidl_bridge_install))
                            }
                        }
                    }
                }
                is AidlBridgeState.Installed -> {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    AidlBridgeReinstallCard(
                        onReinstallClick = { showReinstallDialog = true },
                    )
                }
                is AidlBridgeState.Failed -> {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download Failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = "Failed to download AIDL Bridge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        downloadAndInstallAidlBridge(context) { state ->
                                            aidlBridgeState = state
                                        }
                                    }
                                },
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        if (showReinstallDialog) {
            AidlBridgeReinstallDialog(
                onConfirm = {
                    showReinstallDialog = false
                    scope.launch {
                        downloadAndInstallAidlBridge(context) { state ->
                            aidlBridgeState = state
                        }
                    }
                },
                onDismiss = { showReinstallDialog = false },
            )
        }

        if (showRestartDialog) {
            RestartRequiredDialog(
                onConfirm = {
                    showRestartDialog = false
                    restartLawnchair(context)
                },
                onDismiss = { showRestartDialog = false },
            )
        }
    }
}

@Composable
private fun AidlBridgeInstallCard(
    onInstallClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.aidl_bridge_required),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.aidl_bridge_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            OutlinedButton(
                onClick = onInstallClick,
            ) {
                Text(stringResource(R.string.aidl_bridge_install))
            }
        }
    }
}

@Composable
private fun AidlBridgeReinstallCard(
    onReinstallClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.aidl_bridge_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.aidl_bridge_installed_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                OutlinedButton(
                    onClick = onReinstallClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.aidl_bridge_reinstall))
                }
            }

            // Add restart button if AIDL Bridge is not showing in the list
            val providers = getProviders(context)
            if (!providers.any { it.packageName == AIDL_BRIDGE_PACKAGE || it.packageName == AMIRZ_AIDL_PACKAGE }) {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Button(
                    onClick = {
                        // Restart Lawnchair to refresh the feed providers
                        restartLawnchair(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.aidl_bridge_restart_required))
                }
            }
        }
    }
}

@Composable
private fun AidlBridgeReinstallDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.aidl_bridge_already_installed))
        },
        text = {
            Text(text = stringResource(R.string.aidl_bridge_reinstall_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.aidl_bridge_reinstall))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RestartRequiredDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.aidl_bridge_restart_title))
        },
        text = {
            Text(text = stringResource(R.string.aidl_bridge_restart_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.aidl_bridge_restart_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.aidl_bridge_restart_later))
            }
        },
    )
}

private fun isAidlBridgeInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(AIDL_BRIDGE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

private suspend fun downloadAndInstallAidlBridge(
    context: Context,
    onStateChange: (AidlBridgeState) -> Unit,
) {
    onStateChange(AidlBridgeState.Checking)

    withContext(Dispatchers.IO) {
        try {
            onStateChange(AidlBridgeState.Downloading(0f))
            val file = downloadApk(context, AIDL_BRIDGE_URL) { progress ->
                onStateChange(AidlBridgeState.Downloading(progress))
            }
            if (file != null) {
                onStateChange(AidlBridgeState.Downloaded(file))
            } else {
                Log.e(TAG, "Downloaded file is null")
                onStateChange(AidlBridgeState.Failed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            onStateChange(AidlBridgeState.Failed)
        }
    }
}

private suspend fun downloadApk(context: Context, url: String, onProgress: (Float) -> Unit): File? {
    return withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            val apkDir = File(cacheDir, "aidlbridge")
            apkDir.mkdirs()
            val apkFile = File(apkDir, "aidlbridge.apk")
            if (apkFile.exists()) apkFile.delete()

            val connection = URL(url).openConnection()
            val totalBytes = connection.contentLength.toFloat()

            if (totalBytes <= 0) {
                Log.w(TAG, "Content length is invalid: $totalBytes")
                return@withContext null
            }

            connection.getInputStream().use { input ->
                apkFile.outputStream().use { output ->
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
}

private fun installApk(context: Context, file: File) {
    if (!context.hasInstallPermission()) {
        context.requestInstallPermission()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to install APK", e)
    }
}

private fun Context.hasInstallPermission(): Boolean {
    return if (Utilities.ATLEAST_O) {
        try {
            packageManager.canRequestPackageInstalls()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking install permission", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Exception checking install permission", e)
            false
        }
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

private fun restartLawnchair(context: Context) {
    try {
        // Use ProcessPhoenix-like approach to restart the app
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to restart Lawnchair", e)
        // Fallback: just kill the process
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
