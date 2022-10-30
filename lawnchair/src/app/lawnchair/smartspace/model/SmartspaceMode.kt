package app.lawnchair.smartspace.model

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import app.lawnchair.util.isPackageInstalledAndEnabled
import com.android.launcher3.R


sealed class SmartspaceMode(
    @StringRes val nameResourceId: Int,
    @LayoutRes val layoutResourceId: Int,
) {
    companion object {
        fun fromString(value: String): SmartspaceMode = when (value) {
            "google" -> GoogleSmartspace
            "google_search" -> GoogleSearchSmartspace
            else -> LawnchairSmartspace
        }

        /**
         * @return The list of all time format options.
         */
        fun values() = listOf(
            LawnchairSmartspace,
            GoogleSmartspace,
            GoogleSearchSmartspace,
        )
    }

    abstract fun isAvailable(context: Context): Boolean
}


object LawnchairSmartspace : SmartspaceMode(
    nameResourceId = R.string.smartspace_mode_lawnchair,
    layoutResourceId = R.layout.smartspace_container,
) {
    override fun toString() = "lawnchair"
    override fun isAvailable(context: Context): Boolean = true
}

object GoogleSearchSmartspace : SmartspaceMode(
    nameResourceId = R.string.smartspace_mode_google_search,
    layoutResourceId = R.layout.search_container_workspace,
) {
    override fun toString(): String = "google_search"

    override fun isAvailable(context: Context): Boolean =
        context.packageManager.isPackageInstalledAndEnabled("com.google.android.googlequicksearchbox")
}

object GoogleSmartspace : SmartspaceMode(
    nameResourceId = R.string.smartspace_mode_google,
    layoutResourceId = R.layout.smartspace_legacy,
) {
    override fun toString(): String = "google"

    override fun isAvailable(context: Context): Boolean =
        context.packageManager.isPackageInstalledAndEnabled("com.google.android.googlequicksearchbox")
}
