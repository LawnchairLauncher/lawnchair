package app.lawnchair.util

import android.content.Context
import android.widget.Toast
import android.os.UserHandle
import android.os.UserManager
import app.lawnchair.LawnchairApp
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.PrivateProfileTracker
import com.android.launcher3.R
import com.android.launcher3.logging.FileLog
import com.patrykmichalik.opto.core.firstBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lawnchair's policy for private space apps that live outside the all apps container.
 *
 * AOSP forbids pinning them outright; Lawnchair makes it a user choice, so these helpers are
 * consulted anywhere the stock behaviour would have been unconditional.
 */
object PrivateSpaceUtils {

    private const val TAG = "PrivateSpaceUtils"

    /**
     * These checks run per item while loading, and per icon while drawing, so a failure repeats for
     * as long as its cause lasts. Toasting each time would bury the device in notices and drown out
     * the very problem being reported, so the user is told once and every occurrence is logged.
     */
    private val warnedAboutFallback = AtomicBoolean(false)

    private fun onCheckFailed(context: Context, what: String, t: Throwable) {
        FileLog.e(TAG, "Private space check failed ($what), using a safe default: $t")
        if (warnedAboutFallback.compareAndSet(false, true)) {
            val appContext = context.applicationContext
            Executors.MAIN_EXECUTOR.execute {
                Toast.makeText(
                    appContext,
                    R.string.private_space_check_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** Whether the user has opted in to pinning private space apps to the home screen. */
    @JvmStatic
    fun isPinningAllowed(context: Context): Boolean = try {
        PreferenceManager2.getInstance(context).allowPinningPrivateSpaceApps.firstBlocking()
    } catch (t: Throwable) {
        // Fall back to the stock behaviour rather than letting a preference read take down a
        // caller; several of these run per item while loading or drawing the workspace.
        onCheckFailed(context, "pinning preference", t)
        false
    }

    /** For call sites deep in model code that have no context to hand. */
    @JvmStatic
    fun isPinningAllowed(): Boolean = isPinningAllowed(LawnchairApp.instance)

    /** Whether a private profile exists and is currently locked. */
    @JvmStatic
    fun isPrivateSpaceLocked(context: Context): Boolean {
        val userCache = UserCache.getInstance(context)
        val user =
            userCache.userProfiles.firstOrNull { userCache.getUserInfo(it).isPrivate }
                ?: return PrivateProfileTracker.getKnownPrivateProfileSerial(context) !=
                    PrivateProfileTracker.INVALID_SERIAL
        return try {
            context.getSystemService(UserManager::class.java)?.isQuietModeEnabled(user) ?: false
        } catch (t: Throwable) {
            true
        }
    }

    /**
     * Whether no view should be created for [info] on the workspace at all.
     *
     * Skipping the view - rather than making it invisible - is what leaves the cell genuinely empty:
     * an invisible child still occupies its cell and silently refuses drops, which would reveal how
     * many private apps are pinned and where. The database row is untouched, and the item is placed
     * again, relocating if its cell has since been taken, when the space is unlocked.
     */
    @JvmStatic
    fun shouldSkipWorkspaceBinding(context: Context, info: ItemInfo?): Boolean =
        isHiddenWhileLocked(context, info)

    /**
     * Whether [info] must not be surfaced anywhere at all right now.
     *
     * Hiding an app is only as good as its least careful presentation: the workspace, the taskbar,
     * a folder preview and the search results each reach the model independently, and any one of
     * them showing the icon undoes the rest.
     */
    @JvmStatic
    fun isHiddenWhileLocked(context: Context, info: ItemInfo?): Boolean {
        // Hiding items inside a container creates states the container was never built for: a
        // folder is meant to hold at least two apps, and Launcher3 collapses one that does not.
        // Filtering contents instead produced single-app folders, gaps inside open folders, and
        // normal apps vanishing when an unrelated one moved out - each needing its own patch. So a
        // container is hidden whole if any of its contents is hidden. It is either entirely itself
        // or absent, and no state appears that the launcher could not have produced on its own.
        if (info is CollectionInfo) {
            return info.getContents().any { isHiddenWhileLocked(context, it) }
        }
        return isLockedPrivateSpaceItem(context, info)
    }

    /**
     * Whether the private profile badge should be stripped from [info]'s icon.
     *
     * Checks the profile type before the preference: this runs for every icon that is drawn, and a
     * [UserCache] map lookup is far cheaper than reading a preference.
     */
    @JvmStatic
    fun shouldHideBadge(context: Context, info: ItemInfo?): Boolean {
        if (!isPrivateSpaceItem(context, info)) return false
        return try {
            PreferenceManager2.getInstance(context).hidePrivateSpaceAppBadge.firstBlocking()
        } catch (t: Throwable) {
            // Runs for every icon drawn; keeping the badge is harmless, crashing is not.
            onCheckFailed(context, "badge preference", t)
            false
        }
    }

    /**
     * Asks the system to unlock the private space.
     *
     * Mirrors the all apps lock pill: the call is only permitted for the current home app, so a
     * [SecurityException] means Lawnchair is not the default launcher and the user is prompted to
     * make it one rather than being left with an icon that silently does nothing.
     */
    @JvmStatic
    fun requestUnlock(context: Context, user: UserHandle) {
        Executors.UI_HELPER_EXECUTOR.post {
            try {
                context.getSystemService(UserManager::class.java)
                    ?.requestQuietModeEnabled(false, user)
            } catch (e: SecurityException) {
                Executors.MAIN_EXECUTOR.execute {
                    ApiWrapper.INSTANCE.get(context).assignDefaultHomeRole(context)
                }
            }
        }
    }

    /** Whether [info] belongs to the private profile. */
    @JvmStatic
    fun isPrivateSpaceItem(context: Context, info: ItemInfo?): Boolean {
        val user = info?.user ?: return false
        return try {
            UserCache.getInstance(context).getUserInfo(user).isPrivate
        } catch (t: Throwable) {
            onCheckFailed(context, "profile lookup", t)
            false
        }
    }

    /**
     * Whether [info] is a private space item that cannot currently be launched, i.e. one that should
     * be rendered as a placeholder rather than a working icon.
     */
    @JvmStatic
    fun isLockedPrivateSpaceItem(context: Context, info: ItemInfo?): Boolean {
        // Cheap bitmask test first: this runs for every workspace icon that is bound or updated,
        // and the overwhelming majority are not disabled at all.
        val flags = (info as? ItemInfoWithIcon)?.runtimeStatusFlags ?: return false
        if (flags and ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER == 0) return false
        return isPrivateSpaceItem(context, info)
    }

}
