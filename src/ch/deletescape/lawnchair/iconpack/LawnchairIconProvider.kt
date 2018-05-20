package ch.deletescape.lawnchair.iconpack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.graphics.DrawableFactory
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.google.android.apps.nexuslauncher.CustomDrawableFactory
import com.google.android.apps.nexuslauncher.CustomIconUtils
import com.google.android.apps.nexuslauncher.DynamicIconProvider
import java.util.*

class LawnchairIconProvider(private val context: Context) : DynamicIconProvider(context) {

    val factory = DrawableFactory.get(context) as CustomDrawableFactory
    var dateOfMonth = 0
    val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (!Utilities.ATLEAST_NOUGAT) {
                val dateOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                if (dateOfMonth == this@LawnchairIconProvider.dateOfMonth) {
                    return
                }
                this@LawnchairIconProvider.dateOfMonth = dateOfMonth
            }
            val apps = LauncherAppsCompat.getInstance(context)
            val model = LauncherAppState.getInstance(context).model
            val shortcutManager = DeepShortcutManager.getInstance(context)
            for (user in UserManagerCompat.getInstance(context).userProfiles) {
                factory.packCalendars.keys.forEach {
                    val pkg = it.packageName
                    if (!apps.getActivityList(pkg, user).isEmpty()) {
                        CustomIconUtils.reloadIcon(shortcutManager, model, user, pkg)
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(dateChangeReceiver, IntentFilter(Intent.ACTION_DATE_CHANGED).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            if (!Utilities.ATLEAST_NOUGAT) {
                addAction(Intent.ACTION_TIME_TICK)
            }
        }, null, Handler(LauncherModel.getWorkerLooper()))
    }

    override fun getIcon(launcherActivityInfo: LauncherActivityInfo, iconDpi: Int, flattenDrawable: Boolean): Drawable {
        return IconPackManager.getInstance(context).getIcon(launcherActivityInfo, iconDpi, flattenDrawable, this)
    }

    fun getDynamicIcon(launcherActivityInfo: LauncherActivityInfo, iconDpi: Int, flattenDrawable: Boolean): Drawable {
        return super.getIcon(launcherActivityInfo, iconDpi, flattenDrawable)
    }
}
