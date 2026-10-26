package app.lawnchair.curated

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Activity-based folders of FOSS apps, shipped in assets/curated_folders.json. */
@Serializable
data class CuratedCatalog(val version: Int = 1, val folders: List<CuratedFolder>) {
    companion object {
        private const val ASSET = "curated_folders.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): CuratedCatalog =
            context.assets.open(ASSET).bufferedReader().use { json.decodeFromString(serializer(), it.readText()) }
    }
}

@Serializable
data class CuratedFolder(val id: String, val title: String, val apps: List<CuratedApp>)

@Serializable
data class CuratedApp(
    @SerialName("package") val packageName: String,
    val name: String,
    /** "fdroid", or a GitHub, GitLab or project URL that Obtainium or a browser can use */
    val source: String? = null,
    /** Other package names of the same app, e.g. its F-Droid build */
    val alt: List<String> = emptyList(),
    val foss: Boolean = true,
    /** Listed only when already installed, e.g. Play Store */
    val ifInstalled: Boolean = false,
) {
    val isFdroid get() = source == SOURCE_FDROID
    val projectUrl get() = source?.takeIf { it.startsWith("http") }

    /** The installed package among the app and its alternatives, if any */
    fun installedPackage(launcherApps: LauncherApps): String? =
        (listOf(packageName) + alt).firstOrNull { pkg ->
            try {
                launcherApps.getActivityList(pkg, Process.myUserHandle()).isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

    companion object {
        const val SOURCE_FDROID = "fdroid"
    }
}
