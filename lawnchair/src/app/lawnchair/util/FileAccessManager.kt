package app.lawnchair.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import app.lawnchair.ui.util.isPlayStoreFlavor
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface FileAccessState {
    data object Full : FileAccessState
    data object Partial : FileAccessState
    data object Denied : FileAccessState
}

/**
 * Manages access to files on the device, considering different Android versions and permission models.
 *
 * This class provides [StateFlow]s to observe the current access state for:
 * - Visual media (photos and videos)
 * - Audio media (music and audio)
 * - All files (MANAGE_EXTERNAL_STORAGE)
 *
 * The access states are determined by checking the relevant permissions based on the Android API level.
 *
 * On API < 33 (before Android 13 Tiramisu):
 * - `READ_EXTERNAL_STORAGE` grants access to all media types.
 *
 * On API 33 (Android 13 Tiramisu):
 * - `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` are required for visual media.
 * - `READ_MEDIA_AUDIO` is required for audio media.
 * - `MANAGE_EXTERNAL_STORAGE` grants access to all files, bypassing the need for granular media permissions.
 *   However, for Play Store builds on API 33+, `MANAGE_EXTERNAL_STORAGE` is generally not available,
 *   so this state will typically be `Denied`.
 *
 * On API 34+ (Android 14 Upside Down Cake and later):
 * - Visual media access can be `Full` (if `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` are granted)
 *   or `Partial` (if `READ_MEDIA_VISUAL_USER_SELECTED` is granted, allowing access to user-selected photos/videos).
 * - Audio media access follows the API 33 model.
 * - "All Files Access" follows the API 33 model.
 *
 * The class uses a singleton pattern (`INSTANCE`) for easy access.
 *
 * @param context The application context, used for checking permissions.
 */
class FileAccessManager private constructor(private val context: Context) : SafeCloseable {
    private val _visualMediaAccessState = MutableStateFlow(getCurrentVisualMediaState())
    private val _audioAccessState = MutableStateFlow(getCurrentAudioState())
    private val _allFilesAccessState = MutableStateFlow(getCurrentAllFilesAccessState())
    private val _hasAnyPermission = MutableStateFlow(hasAnyPermissions())

    private val _wallpaperAccessState = MutableStateFlow(getCurrentWallpaperAccessState())

    /**
     * Represents the state of access to Photos & Videos.
     * On API < 33, this is controlled by READ_EXTERNAL_STORAGE.
     * On API 33, this is READ_MEDIA_IMAGES + READ_MEDIA_VIDEO.
     * On API 34+, this can be Full or Partial.
     */
    val visualMediaAccessState: StateFlow<FileAccessState> = _visualMediaAccessState.asStateFlow()

    /**
     * Represents the state of access to Music & Audio.
     * On API < 33, this is controlled by READ_EXTERNAL_STORAGE.
     * On API 33+, this is READ_MEDIA_AUDIO.
     */
    val audioAccessState: StateFlow<FileAccessState> = _audioAccessState.asStateFlow()

    /**
     * Represents the state of "All Files Access". Having All Files Access bypasses the need to request visual and audio access.
     * On API < 33, this is controlled by READ_EXTERNAL_STORAGE.
     * On API 33+, this is controlled by MANAGE_EXTERNAL_STORAGE.
     * If on API 33+, this should always be `false` for Play Store builds.
     */
    val allFilesAccessState: StateFlow<FileAccessState> = _allFilesAccessState.asStateFlow()

    /**
     * A [StateFlow] indicating if the app has any level of file access permission.
     * This is true if access to "All Files", "Visual Media", or "Audio Media" is not `Denied`.
     * Useful for determining if it's worth showing any file-related features or permission requests.
     */
    val hasAnyPermission: StateFlow<Boolean> = _hasAnyPermission.asStateFlow()

    /**
     * Determines the current access state for reading the device wallpaper.
     * This is needed to use [android.app.WallpaperManager.getDrawable].
     *
     * The permission requirements vary by Android version:
     * - **API 29 (Android 10) and below:** `READ_EXTERNAL_STORAGE` is required.
     *   This is covered if [allFilesAccessState] returns `Full`.
     * - **API 30-32 (Android 11 - Android 12L):** `MANAGE_EXTERNAL_STORAGE` is required.
     *   This is covered if [allFilesAccessState] returns `Full`.
     * - **API 33 (Android 13 Tiramisu):** `MANAGE_EXTERNAL_STORAGE` **OR**
     *   (`READ_MEDIA_IMAGES` **AND** `READ_MEDIA_VIDEO`) are required.
     *   If `MANAGE_EXTERNAL_STORAGE` is not available (e.g., Play Store builds),
     *   then `Full` access to visual media via `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` is necessary.
     * - **API 34+ (Android 14 Upside Down Cake and later):** `MANAGE_EXTERNAL_STORAGE` **OR**
     *   (`READ_MEDIA_IMAGES` **AND** `READ_MEDIA_VIDEO`) are required.
     *   `READ_MEDIA_VISUAL_USER_SELECTED` (partial access) is **not sufficient** for wallpaper access.
     *   Similar to API 33, if `MANAGE_EXTERNAL_STORAGE` is unavailable, full visual media access is required.
     *
     * In essence, for API 33 and above, if the app doesn't have `MANAGE_EXTERNAL_STORAGE`,
     * it needs full `visualMediaAccessState` to read the wallpaper.
     * `Partial` visual media access is insufficient.
     */
    val wallpaperAccessState: StateFlow<FileAccessState> = _wallpaperAccessState.asStateFlow()

    /**
     * Re-evaluates the current permission status and updates all StateFlows.
     * This should be called when the app resumes to catch any permission
     * changes made by the user in system settings.
     */
    fun refresh() {
        // We must re-check all files first, as the media states depend on it.
        _allFilesAccessState.value = getCurrentAllFilesAccessState()
        _visualMediaAccessState.value = getCurrentVisualMediaState()
        _audioAccessState.value = getCurrentAudioState()
        _wallpaperAccessState.value = getCurrentWallpaperAccessState()
        _hasAnyPermission.value = hasAnyPermissions()
    }

    private fun hasAnyPermissions(): Boolean {
        return _visualMediaAccessState.value != FileAccessState.Denied ||
            _audioAccessState.value != FileAccessState.Denied ||
            _allFilesAccessState.value != FileAccessState.Denied
    }

    private fun getCurrentWallpaperAccessState(): FileAccessState {
        if (getCurrentAllFilesAccessState() == FileAccessState.Full) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkPermission(Manifest.permission.READ_MEDIA_IMAGES) && checkPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                    return FileAccessState.Full
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    checkPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                ) {
                    // Partial access only. Wallpaper access requires full media access
                    return FileAccessState.Denied
                }
            } else {
                return FileAccessState.Full
            }
        }

        return FileAccessState.Denied
    }

    private fun getCurrentVisualMediaState(): FileAccessState {
        if (getCurrentAllFilesAccessState() == FileAccessState.Full) {
            return FileAccessState.Full
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkPermission(Manifest.permission.READ_MEDIA_IMAGES) && checkPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                return FileAccessState.Full
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                checkPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            ) {
                // Partial access (Android 14+)
                return FileAccessState.Partial
            }
        }

        return FileAccessState.Denied
    }

    private fun getCurrentAudioState(): FileAccessState {
        if (getCurrentAllFilesAccessState() == FileAccessState.Full) {
            return FileAccessState.Full
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                return FileAccessState.Full
            }
        }

        return FileAccessState.Denied
    }

    private fun getCurrentAllFilesAccessState(): FileAccessState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPlayStoreFlavor()) {
                // MANAGE_EXTERNAL_STORAGE is disabled for Play Store releases
                return FileAccessState.Denied
            }

            if (Environment.isExternalStorageManager()) {
                return FileAccessState.Full
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                return FileAccessState.Full
            }
        }
        return FileAccessState.Denied
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun close() {}

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::FileAccessManager)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}
