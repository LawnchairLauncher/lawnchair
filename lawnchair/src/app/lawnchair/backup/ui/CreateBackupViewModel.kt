package app.lawnchair.backup.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.view.View.MeasureSpec.EXACTLY
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.util.FileAccessManager
import app.lawnchair.views.LauncherPreviewView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapRenderer
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class CreateBackupViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val screenshot = MutableStateFlow(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    val screenshotDone = MutableStateFlow(false)

    private val fileAccessManager = FileAccessManager.getInstance(application)
    val allFilesAccessState = fileAccessManager.allFilesAccessState
    val wallpaperAccessState = fileAccessManager.wallpaperAccessState

    fun refreshFilePermissionStates() {
        fileAccessManager.refresh()
    }

    val backupContents = savedStateHandle.getStateFlow(
        "contents",
        LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS or LawnchairBackup.INCLUDE_WALLPAPER,
    )

    init {
        viewModelScope.launch {
            captureScreenshot()?.let { screenshot.value = it }
            screenshotDone.value = true
        }
    }

    private suspend fun captureScreenshot(): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            val app = getApplication<Application>()

            val config = Configuration(app.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                val width = screenWidthDp
                val height = screenHeightDp
                screenWidthDp = min(width, height)
                screenHeightDp = max(width, height)
            }
            val context = app.createConfigurationContext(config)

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
                val width = dp.widthPx
                val height = dp.heightPx
                previewView.measure(
                    MeasureSpec.makeMeasureSpec(width, EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, EXACTLY),
                )
                previewView.layout(0, 0, width, height)
                val bitmap = BitmapRenderer.createHardwareBitmap(width, height, previewView::draw)
                continuation.resume(bitmap)
                previewView.destroy()
            }
        }
    }

    fun setBackupContents(contents: Int) {
        savedStateHandle["contents"] = contents
    }
}
