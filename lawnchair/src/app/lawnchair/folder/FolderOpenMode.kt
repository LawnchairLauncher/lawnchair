package app.lawnchair.folder

import androidx.annotation.StringRes
import com.android.launcher3.R

sealed class FolderOpenMode(
    @StringRes val nameResourceId: Int,
) {
    companion object {
        fun fromString(value: String): FolderOpenMode = when (value) {
            "aosp" -> Aosp
            else -> Centered
        }

        fun values() = listOf(Centered, Aosp)
    }
}

/** Folder always opens centered on screen. */
object Centered : FolderOpenMode(
    nameResourceId = R.string.folder_open_mode_centered,
) {
    override fun toString() = "centered"
}

/** Folder opens relative to the folder icon position (AOSP behavior). */
object Aosp : FolderOpenMode(
    nameResourceId = R.string.folder_open_mode_aosp,
) {
    override fun toString() = "aosp"
}
