package app.lawnchair.search

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Process
import androidx.core.os.bundleOf
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.launcher
import app.lawnchair.search.adapter.SearchActionCompat
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.UserProfileManager
import com.android.launcher3.pm.UserCache

object PrivateSpaceSearchUtils {

    const val PRIVATE_SPACE_ACTION = "app.lawnchair.action.UNLOCK_PRIVATE_SPACE"
    const val PRIVATE_SPACE_PACKAGE = "com.android.privatespace"

    fun hasPrivateSpace(context: Context): Boolean {
        val userCache = UserCache.getInstance(context)
        return userCache.userProfiles.any { userCache.getUserInfo(it).isPrivate }
    }

    fun isPrivateSpaceLocked(context: Context): Boolean {
        return try {
            val launcher = context.launcher
            val appsView = launcher.appsView as? ActivityAllAppsContainerView<*>
            val ppm = appsView?.privateProfileManager
            ppm?.currentState != UserProfileManager.STATE_ENABLED
        } catch (_: Exception) {
            true
        }
    }

    fun matchesPrivateSpace(context: Context, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty() || q.length < 2) return false

        val psLabel = context.getString(R.string.private_space_label).lowercase()
        val psTitle = context.getString(R.string.ps_container_title).lowercase()

        return q == "ps" ||
            q == "private" ||
            q == "private space" ||
            q == "privatespace" ||
            "private space".startsWith(q) ||
            "privatespace".startsWith(q) ||
            psLabel.startsWith(q) ||
            psTitle.startsWith(q) ||
            (q.length >= 3 && (psLabel.contains(q) || psTitle.contains(q)))
    }

    fun shouldShowPrivateSpaceInSearch(context: Context, query: String): Boolean {
        if (!hasPrivateSpace(context)) return false
        return matchesPrivateSpace(context, query)
    }

    fun createPrivateSpaceTarget(context: Context, isLocked: Boolean): SearchTargetCompat {
        val id = "action:private_space"
        val title = context.getString(R.string.private_space_label)
        val subtitle = if (isLocked) {
            context.getString(R.string.ps_container_lock_button_content_description)
        } else {
            context.getString(R.string.ps_container_unlock_button_content_description)
        }
        val icon = Icon.createWithResource(context, R.drawable.ic_private_space_with_background)
        val action = SearchActionCompat.Builder(id, title)
            .setIcon(icon)
            .setSubtitle(subtitle)
            .setIntent(Intent(PRIVATE_SPACE_ACTION))
            .build()
        val extras = bundleOf(
            SearchResultView.EXTRA_HIDE_SUBTITLE to false,
            SearchResultView.EXTRA_QUICK_LAUNCH to true,
        )
        return SearchTargetCompat.Builder(
            SearchTargetCompat.RESULT_TYPE_REDIRECTION,
            LayoutType.ICON_HORIZONTAL_TEXT,
            SearchTargetFactory.generateHashKey(id),
        )
            .setPackageName(PRIVATE_SPACE_PACKAGE)
            .setUserHandle(Process.myUserHandle())
            .setSearchAction(action)
            .setExtras(extras)
            .build()
    }

    fun unlockOrOpenPrivateSpace(context: Context) {
        val launcher = context.launcher
        val appsView = launcher.appsView as? ActivityAllAppsContainerView<*> ?: return
        val privateProfileManager = appsView.privateProfileManager ?: return
        if (privateProfileManager.currentState == UserProfileManager.STATE_DISABLED) {
            privateProfileManager.setQuietMode(false)
        } else {
            appsView.searchUiManager.resetSearch()
            appsView.switchToTab(ActivityAllAppsContainerView.AdapterHolder.MAIN)
            privateProfileManager.expandPrivateSpace()
        }
    }
}
