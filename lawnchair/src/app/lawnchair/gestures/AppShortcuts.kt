package app.lawnchair.gestures

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.UserHandle
import android.util.Log
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.util.toBitmap
import com.android.launcher3.pm.UserCache

/** LC: one app shortcut (dynamic, manifest or pinned) that a gesture can open. */
data class AppShortcutEntry(
    val label: String,
    val icon: Bitmap,
    val target: OpenAppTarget.Shortcut,
) {
    fun toGestureConfig(): GestureHandlerConfig = GestureHandlerConfig.OpenApp(appName = label, target = target)
}

object AppShortcuts {

    /**
     * Loads the app shortcuts this launcher may start. With [packageName] only that app's
     * shortcuts are queried (optionally only for [user]), which is much cheaper than all apps.
     * Must not be called on the main thread.
     */
    fun load(
        context: Context,
        packageName: String? = null,
        user: UserHandle? = null,
    ): List<AppShortcutEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val pm = context.packageManager
        val density = context.resources.displayMetrics.densityDpi
        val query = ShortcutQuery().setQueryFlags(
            ShortcutQuery.FLAG_MATCH_DYNAMIC or
                ShortcutQuery.FLAG_MATCH_MANIFEST or
                ShortcutQuery.FLAG_MATCH_PINNED,
        )
        if (packageName != null) query.setPackage(packageName)
        val users = if (user != null) listOf(user) else UserCache.INSTANCE.get(context).userProfiles
        val result = mutableListOf<AppShortcutEntry>()
        for (profile in users) {
            val infos: List<ShortcutInfo> = try {
                launcherApps.getShortcuts(query, profile).orEmpty()
            } catch (e: Exception) {
                Log.w("AppShortcuts", "Failed to query shortcuts", e)
                emptyList()
            }
            for (info in infos) {
                if (!info.isEnabled) continue
                val appLabel = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.`package`, 0)).toString()
                } catch (_: Exception) {
                    info.`package`
                }
                val shortcutLabel = (info.longLabel ?: info.shortLabel)?.toString().orEmpty()
                // Badged for its profile, so work and personal shortcuts can be told apart.
                val icon = try {
                    launcherApps.getShortcutBadgedIconDrawable(info, density)
                } catch (_: Exception) {
                    null
                } ?: pm.defaultActivityIcon
                val label = if (shortcutLabel.isEmpty()) appLabel else "$appLabel: $shortcutLabel"
                result += AppShortcutEntry(
                    // Adds e.g. "Work" for shortcuts of another profile (also read by TalkBack).
                    label = pm.getUserBadgedLabel(label, info.userHandle).toString(),
                    icon = icon.toBitmap(),
                    target = OpenAppTarget.Shortcut(
                        intent = Intent(Intent.ACTION_MAIN).setPackage(info.`package`),
                        user = info.userHandle,
                        packageName = info.`package`,
                        id = info.id,
                    ),
                )
            }
        }
        return result.distinctBy { Triple(it.target.packageName, it.target.id, it.target.user) }
            .sortedBy { it.label.lowercase() }
    }
}
