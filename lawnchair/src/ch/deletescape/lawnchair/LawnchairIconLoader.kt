/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import android.util.LruCache
import com.android.launcher3.Utilities
import com.android.quickstep.NormalizedIconLoader
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.TaskKeyLruCache

@RequiresApi(Build.VERSION_CODES.P)
class LawnchairIconLoader(private val context: Context, iconCache: TaskKeyLruCache<Drawable>,
                          activityInfoCache: LruCache<ComponentName, ActivityInfo>) :
        NormalizedIconLoader(context, iconCache, activityInfoCache) {

    private val TAG = "LcIconLoader"

    @SuppressLint("WrongConstant")
    override fun createNewIconForTask(taskKey: Task.TaskKey, desc: ActivityManager.TaskDescription,
                                      returnDefault: Boolean): Drawable? {
        val userId = taskKey.userId
        val iconResource = HiddenApiCompat.getIconResource(desc)
        if (iconResource != 0) {
            try {
                val pm = mContext.packageManager
                val appInfo = pm.getApplicationInfo(taskKey.packageName, 0x00400000)
                val res = pm.getResourcesForApplication(appInfo)
                return createBadgedDrawable(res.getDrawable(iconResource, null), userId, desc)
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Could not find icon drawable from resource", e)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Could not find icon drawable from resource", e)
            }
        }

        HiddenApiCompat.loadTaskDescriptionIcon(desc, userId)?.let { tdIcon ->
            return createDrawableFromBitmap(tdIcon, userId, desc)
        }

        getAndUpdateActivityInfo(taskKey)?.let { activityInfo ->
            val icon = Utilities.getIconForTask(context, userId, activityInfo.packageName)
            getBadgedActivityIcon(icon, activityInfo, userId, desc)?.let {
                return it
            }
        }
        return if (returnDefault) getDefaultIcon(taskKey.userId) else null
    }

    private fun getBadgedActivityIcon(icon: Drawable, activityInfo: ActivityInfo, userId: Int,
                                      desc: ActivityManager.TaskDescription): Drawable? {
        val bitmapInfo = getBitmapInfo(icon, userId, desc.primaryColor,
                HiddenApiCompat.isInstantApp(activityInfo.applicationInfo))
        return mDrawableFactory.newIcon(bitmapInfo, activityInfo)
    }
}
