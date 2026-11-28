package app.lawnchair.backup

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.view.View.MeasureSpec.EXACTLY
import androidx.core.content.edit
import androidx.core.net.toUri
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.views.LauncherPreviewView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LC: backs up layout and settings on its own into a folder the user picked, like the manual
 * backup, and keeps only the newest few. It runs when the home screen is left (so the preview
 * is drawn while nobody looks at the launcher) and a backup is due.
 */
object AutoBackup {

    private const val TAG = "AutoBackup"
    private const val FILE_PREFIX = "Lawnchair_Auto_Backup "
    private const val STATE_PREFS = "auto_backup_state"
    private const val KEY_LAST_TIME = "last_time"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val RETRY_MS = 60L * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val PREVIEW_TIMEOUT_MS = 5000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running = false

    /** Last result for the settings screen: time of the last backup and the last error. */
    data class Status(val lastTime: Long, val lastError: String?)

    private val statusFlow = MutableStateFlow<Status?>(null)

    fun status(context: Context): StateFlow<Status?> {
        if (statusFlow.value == null) statusFlow.value = readStatus(context)
        return statusFlow.asStateFlow()
    }

    /** Called when the home screen is left; starts a backup if one is due. */
    fun onLauncherStopped(context: Context) {
        val prefs = PreferenceManager2.getInstance(context)
        if (!prefs.autoBackupEnabled.firstCached(prefs)) return
        if (prefs.autoBackupFolder.firstCached(prefs).isEmpty()) return
        val interval = prefs.autoBackupIntervalDays.firstCached(prefs).coerceAtLeast(1) * DAY_MS
        val now = System.currentTimeMillis()
        if (now - readStatus(context).lastTime < interval) return
        // After a failed attempt (e.g. the folder is gone), wait before trying again.
        val state = stateFile(context)
        if (now - state.getLong(KEY_LAST_ATTEMPT, 0L) < RETRY_MS) return
        state.edit { putLong(KEY_LAST_ATTEMPT, now) }
        backUpNow(context.applicationContext)
    }

    /** Starts a backup now, unless one is already running. */
    fun backUpNow(context: Context) {
        if (running) return
        running = true
        scope.launch {
            try {
                backUp(context)
                writeStatus(context, Status(System.currentTimeMillis(), null))
            } catch (e: Exception) {
                Log.w(TAG, "Automatic backup failed", e)
                writeStatus(context, Status(readStatus(context).lastTime, e.message ?: e.javaClass.simpleName))
            } finally {
                running = false
            }
        }
    }

    private suspend fun backUp(context: Context) {
        val prefs = PreferenceManager2.getInstance(context)
        val folder = prefs.autoBackupFolder.firstCached(prefs).toUri()
        val keep = prefs.autoBackupKeep.firstCached(prefs).coerceAtLeast(1)
        val preview = withTimeoutOrNull(PREVIEW_TIMEOUT_MS) { capturePreview(context) }
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val name = FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.ROOT).format(Date())
            val file = DocumentsContract.createDocument(resolver, parent, LawnchairBackup.MIME_TYPE, "$name.lawnchairbackup")
                ?: error(context.getString(R.string.auto_backup_error_folder))
            LawnchairBackup.create(context, LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS, preview, file)
            deleteOldBackups(context, folder, keep)
        }
    }

    /** Deletes all but the newest [keep] automatic backups in [folder]. Names sort by date. */
    private fun deleteOldBackups(context: Context, folder: Uri, keep: Int) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val backups = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(FILE_PREFIX)) backups += cursor.getString(0) to name
            }
        }
        backups.sortedByDescending { it.second }.drop(keep).forEach { (id, name) ->
            try {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id))
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete old backup $name", e)
            }
        }
    }

    /** Draws the home screen like the manual backup does, for the preview in the restore screen. */
    private suspend fun capturePreview(appContext: Context): Bitmap = suspendCancellableCoroutine { continuation ->
        val config = Configuration(appContext.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            val width = screenWidthDp
            val height = screenHeightDp
            screenWidthDp = min(width, height)
            screenHeightDp = max(width, height)
        }
        val context = appContext.createConfigurationContext(config)
        val idp = LauncherAppState.getIDP(context)
        val themedContext = ContextThemeWrapper(context, R.style.Theme_Lawnchair)
        val previewView = LauncherPreviewView(
            context = themedContext,
            idp = idp,
            dummyInsets = true,
            dummySmartspace = true,
            appContext = themedContext,
        )
        continuation.invokeOnCancellation { previewView.destroy() }
        previewView.addOnReadyCallback {
            val dp = idp.getDeviceProfile(context)
            val width = dp.deviceProperties.widthPx
            val height = dp.deviceProperties.heightPx
            previewView.measure(
                MeasureSpec.makeMeasureSpec(width, EXACTLY),
                MeasureSpec.makeMeasureSpec(height, EXACTLY),
            )
            previewView.layout(0, 0, width, height)
            val bitmap = BitmapRenderer.createHardwareBitmap(width, height, previewView::draw)
            previewView.destroy()
            if (continuation.isActive) continuation.resume(bitmap)
        }
    }

    // Kept outside the backed up preferences: a restored backup must not bring back old times.
    private fun stateFile(context: Context) = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    private fun readStatus(context: Context): Status {
        val prefs = stateFile(context)
        return Status(prefs.getLong(KEY_LAST_TIME, 0L), prefs.getString(KEY_LAST_ERROR, null))
    }

    private fun writeStatus(context: Context, status: Status) {
        stateFile(context).edit {
            putLong(KEY_LAST_TIME, status.lastTime)
            putString(KEY_LAST_ERROR, status.lastError)
        }
        statusFlow.value = status
    }
}
