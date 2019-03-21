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
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.RequiresApi
import android.text.TextUtils
import ch.deletescape.lawnchair.getLauncherActivityInfo
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.graphics.*
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.DynamicIconProvider
import com.google.android.apps.nexuslauncher.clock.DynamicClock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class DefaultPack(context: Context) : IconPack(context, "") {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val wrapperIcon: Drawable by lazy { context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper)!!.mutate() }
    private val normalizer: IconNormalizer by lazy { LauncherIcons.obtain(context).normalizer }
    private val dynamicClockDrawer = DynamicClock(context)
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

    override fun getIcon(launcherActivityInfo: LauncherActivityInfo,
                         iconDpi: Int, flattenDrawable: Boolean, customIconEntry: IconPackManager.CustomIconEntry?,
                         basePacks: Iterator<IconPack>, iconProvider: LawnchairIconProvider?): Drawable {
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
            if (Utilities.ATLEAST_OREO && shouldWrapToAdaptive(originalIcon)) {
                return wrapToAdaptiveIcon(roundIcon ?: originalIcon)
            }
            if (roundIcon != null) return roundIcon as Drawable
        }
        return iconProvider?.getDynamicIcon(info, iconDpi, flattenDrawable) ?: originalIcon
    }

    override fun getIcon(shortcutInfo: ShortcutInfoCompat, iconDpi: Int, basePacks: Iterator<IconPack>): Drawable {
        ensureInitialLoadComplete()

        val drawable = DeepShortcutManager.getInstance(context).getShortcutIconDrawable(shortcutInfo, iconDpi)
        if (Utilities.ATLEAST_OREO && shouldWrapToAdaptive(drawable)) {
            return wrapToAdaptiveIcon(drawable)
        }
        return drawable
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun shouldWrapToAdaptive(icon: Drawable): Boolean {
        val ipm = IconPackManager.getInstance(context)
        if (!prefs.enableLegacyTreatment || (prefs.iconPackMasking && ipm.maskSupported())) {
            return false
        }
        return if (icon is AdaptiveIconDrawable) {
            prefs.colorizedLegacyTreatment &&
                    prefs.enableWhiteOnlyTreatment &&
                    ColorExtractor.isSingleColor(icon.background, Color.WHITE) &&
                    icon.foreground != null
        } else true

    }

    override fun newIcon(icon: Bitmap, itemInfo: ItemInfo, customIconEntry: IconPackManager.CustomIconEntry?,
                         basePacks: Iterator<IconPack>, drawableFactory: LawnchairDrawableFactory): FastBitmapDrawable {
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun wrapToAdaptiveIcon(icon: Drawable): Drawable {
        return if (icon is AdaptiveIconDrawable && icon.foreground != null) {
            if (icon.background is ColorDrawable)
                icon.apply {
                    (background as? ColorDrawable)?.color = extractColor(foreground)
                }
            else
                AdaptiveIconDrawable(ColorDrawable(extractColor(icon.foreground)), icon.foreground)
        } else {
            val dr = (wrapperIcon as AdaptiveIconDrawable).apply {
                mutate()
                setBounds(0, 0, 1, 1)
            }
            val outShape = BooleanArray(1)
            val scale = normalizer.getScale(icon, null, dr.iconMask, outShape)
            if (!outShape[0]) {
                dr.apply {
                    (dr.foreground as FixedScaleDrawable).drawable = icon
                    (dr.background as ColorDrawable).color = extractColor(icon)
                }
            } else {
                icon
            }
        }
    }

    private fun extractColor(drawable: Drawable): Int = if (prefs.colorizedLegacyTreatment) {
        ColorExtractor.generateBackgroundColor(drawable.toBitmap())
    } else {
        Color.WHITE
    }

    class Entry(private val app: LauncherActivityInfo) : IconPack.Entry() {

        override val displayName by lazy { app.label.toString() }
        override val identifierName = ComponentKey(app.componentName, app.user).toString()
        override val drawable get() = app.getIcon(0)!!

        override fun toCustomEntry() = IconPackManager.CustomIconEntry("", ComponentKey(app.componentName, app.user).toString())
    }
}
