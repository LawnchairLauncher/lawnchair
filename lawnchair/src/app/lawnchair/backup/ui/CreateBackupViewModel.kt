package app.lawnchair.backup.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.view.View.MeasureSpec.EXACTLY
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.util.FileAccessManager
import app.lawnchair.views.LauncherPreviewView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.icons.BitmapRenderer
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
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

    /** The screen the preview was last captured for, see [captureScreenshotFor]. */
    private var capturedScreen: List<Int>? = null
    private var captureJob: Job? = null

    /**
     * Captures the home screen preview of the screen [activity] is on, with its [config], unless it
     * was already captured for that screen. On a foldable this is called again when the device
     * folds or unfolds (the view model outlives the change): the preview is of the screen it is
     * shown on, the cover's layout or the large screen's, and so is the one saved in the backup.
     * Rotating keeps the preview, which is always taken upright, and so does a window resized in
     * split screen: the preview is of the whole screen.
     */
    fun captureScreenshotFor(activity: Context, config: Configuration) {
        var screenWidth = config.screenWidthDp
        var screenHeight = config.screenHeightDp
        if (Utilities.ATLEAST_R) {
            val bounds = activity.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val density = config.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
            screenWidth = (bounds.width() / density).roundToInt()
            screenHeight = (bounds.height() / density).roundToInt()
        }
        val width = min(screenWidth, screenHeight)
        val height = max(screenWidth, screenHeight)
        val screen = listOf(width, height, config.densityDpi)
        if (screen == capturedScreen) return
        capturedScreen = screen
        captureJob?.cancel()
        screenshotDone.value = false
        val portrait = Configuration(config).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = width
            screenHeightDp = height
        }
        captureJob = viewModelScope.launch {
            captureScreenshot(portrait)?.let { screenshot.value = it }
            screenshotDone.value = true
        }
    }

    private suspend fun captureScreenshot(config: Configuration): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            val app = getApplication<Application>()
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
                val width = dp.deviceProperties.widthPx
                val height = dp.deviceProperties.heightPx
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
