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

package ch.deletescape.lawnchair.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.TextUtils
import ch.deletescape.lawnchair.adaptive.AdaptiveIconGenerator
import ch.deletescape.lawnchair.getLauncherActivityInfo
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.DynamicIconProvider
import com.google.android.apps.nexuslauncher.clock.DynamicClock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class DefaultPack(context: Context) : IconPack(context, "") {

    val dynamicClockDrawer by lazy { DynamicClock(context) }
    private val appMap = HashMap<ComponentKey, Entry>().apply {
        val launcherApps = LauncherAppsCompat.getInstance(context)
        UserManagerCompat.getInstance(context).userProfiles.forEach { user ->
            launcherApps.getActivityList(null, user).forEach {
                put(ComponentKey(it.componentName, user), Entry(it))
            }
        }
    }
    override val entries get() = appMap.values.toList()

    init {
        executeLoadPack()
    }

    override val packInfo = IconPackList.DefaultPackInfo(context)

    override fun onDateChanged() {
        val model = LauncherAppState.getInstance(context).model
        UserManagerCompat.getInstance(context).userProfiles.forEach { user ->
            model.onPackageChanged(DynamicIconProvider.GOOGLE_CALENDAR, user)
            val shortcuts = DeepShortcutManager.getInstance(context).queryForPinnedShortcuts(DynamicIconProvider.GOOGLE_CALENDAR, user)
            if (!shortcuts.isEmpty()) {
                model.updatePinnedShortcuts(DynamicIconProvider.GOOGLE_CALENDAR, shortcuts, user)
            }
        }
    }

    override fun loadPack() {

    }

    override fun getEntryForComponent(key: ComponentKey) = appMap[key]

    override fun getIcon(entry: IconPackManager.CustomIconEntry, iconDpi: Int): Drawable? {
        return getIcon(ComponentKey(context, entry.icon), iconDpi)
    }

    fun getIcon(key: ComponentKey, iconDpi: Int): Drawable? {
        ensureInitialLoadComplete()

        val info = key.getLauncherActivityInfo(context) ?: return null
        val component = key.componentName
        val originalIcon = info.getIcon(iconDpi).apply { mutate() }
        var roundIcon: Drawable? = null
        getRoundIcon(component, iconDpi)?.let {
            roundIcon = it.apply { mutate() }
        }
        val gen = AdaptiveIconGenerator(context, roundIcon ?: originalIcon)
        return gen.result
    }

    override fun getIcon(launcherActivityInfo: LauncherActivityInfo,
                         iconDpi: Int, flattenDrawable: Boolean,
                         customIconEntry: IconPackManager.CustomIconEntry?,
                         iconProvider: LawnchairIconProvider?): Drawable {
        ensureInitialLoadComplete()

        val key: ComponentKey
        val info: LauncherActivityInfo
        if (customIconEntry != null && !TextUtils.isEmpty(customIconEntry.icon)) {
            key = ComponentKey(context, customIconEntry.icon)
            info = key.getLauncherActivityInfo(context) ?: launcherActivityInfo
        } else {
            key = ComponentKey(launcherActivityInfo.componentName, launcherActivityInfo.user)
            info = launcherActivityInfo
        }
        val component = key.componentName
        val packageName = component.packageName
        val originalIcon = info.getIcon(iconDpi).apply { mutate() }
        if (iconProvider == null || (DynamicIconProvider.GOOGLE_CALENDAR != packageName && DynamicClock.DESK_CLOCK != component)) {
            var roundIcon: Drawable? = null
            getRoundIcon(component, iconDpi)?.let {
                roundIcon = it.apply { mutate() }
            }
            val gen = AdaptiveIconGenerator(context, roundIcon ?: originalIcon)
            return gen.result
        }
        return iconProvider.getDynamicIcon(info, iconDpi, flattenDrawable)
    }

    override fun getIcon(shortcutInfo: ShortcutInfoCompat, iconDpi: Int): Drawable? {
        ensureInitialLoadComplete()

        val drawable = DeepShortcutManager.getInstance(context).getShortcutIconDrawable(shortcutInfo, iconDpi)
        val gen = AdaptiveIconGenerator(context, drawable)
        return gen.result
    }

    override fun newIcon(icon: Bitmap, itemInfo: ItemInfo,
                         customIconEntry: IconPackManager.CustomIconEntry?,
                         drawableFactory: LawnchairDrawableFactory): FastBitmapDrawable {
        ensureInitialLoadComplete()

        if (Utilities.ATLEAST_OREO && itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            val component = if (customIconEntry?.icon != null) {
                ComponentKey(context, customIconEntry.icon).componentName
            } else {
                itemInfo.targetComponent
            }
            if (DynamicClock.DESK_CLOCK == component) {
                return dynamicClockDrawer.drawIcon(icon)
            }
        }

        return FastBitmapDrawable(icon)
    }

    override fun supportsMasking(): Boolean = false

    private fun getRoundIcon(component: ComponentName, iconDpi: Int): Drawable? {
        var appIcon: String? = null
        val elementTags = HashMap<String, String>()

        try {
            val resourcesForApplication = context.packageManager.getResourcesForApplication(component.packageName)
            val assets = resourcesForApplication.assets

            val parseXml = assets.openXmlResourceParser("AndroidManifest.xml")
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType == XmlPullParser.START_TAG) {
                    val name = parseXml.name
                    for (i in 0 until parseXml.attributeCount) {
                        elementTags[parseXml.getAttributeName(i)] = parseXml.getAttributeValue(i)
                    }
                    if (elementTags.containsKey("roundIcon")) {
                        if (name == "application") {
                            appIcon = elementTags["roundIcon"]
                        } else if ((name == "activity" || name == "activity-alias") &&
                                elementTags.containsKey("name") &&
                                elementTags["name"] == component.className) {
                            appIcon = elementTags["roundIcon"]
                            break
                        }
                    }
                    elementTags.clear()
                }
            }
            parseXml.close()

            if (appIcon != null) {
                val resId = Utilities.parseResourceIdentifier(resourcesForApplication, appIcon, component.packageName)
                return resourcesForApplication.getDrawableForDensity(resId, iconDpi)
            }
        } catch (ex: PackageManager.NameNotFoundException) {
            ex.printStackTrace()
        } catch (ex: Resources.NotFoundException) {
            ex.printStackTrace()
        } catch (ex: IOException) {
            ex.printStackTrace()
        } catch (ex: XmlPullParserException) {
            ex.printStackTrace()
        }

        return null
    }

    class Entry(private val app: LauncherActivityInfo) : IconPack.Entry() {

        override val displayName by lazy { app.label.toString() }
        override val identifierName = ComponentKey(app.componentName, app.user).toString()
        override val isAvailable = true

        override fun drawableForDensity(density: Int): Drawable {
            return AdaptiveIconCompat.wrap(app.getIcon(density)!!)
        }

        override fun toCustomEntry() = IconPackManager.CustomIconEntry("", ComponentKey(app.componentName, app.user).toString())
    }
}
