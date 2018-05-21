package ch.deletescape.lawnchair.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.TextUtils
import ch.deletescape.lawnchair.getLauncherActivityInfo
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.DynamicIconProvider
import com.google.android.apps.nexuslauncher.clock.DynamicClock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class DefaultPack(context: Context) : IconPack(context, "") {

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

    override val displayIcon by lazy { context.getDrawable(R.mipmap.ic_launcher_round)!! }
    override val displayName by lazy { context.resources.getString(R.string.icon_pack_default)!! }

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
                         basePack: IconPack, iconProvider: LawnchairIconProvider?): Drawable {
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
        if (iconProvider == null || (DynamicIconProvider.GOOGLE_CALENDAR != packageName && DynamicClock.DESK_CLOCK != component)) {
            getRoundIcon(component, iconDpi)?.let {
                return it.apply { mutate() }
            }
        }
        return iconProvider?.getDynamicIcon(info, iconDpi, flattenDrawable) ?: info.getIcon(iconDpi)
    }

    override fun newIcon(icon: Bitmap, itemInfo: ItemInfo, customIconEntry: IconPackManager.CustomIconEntry?,
                         basePack: IconPack, drawableFactory: LawnchairDrawableFactory): FastBitmapDrawable {
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
                val resId = Integer.parseInt(appIcon.substring(1))
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

        override val displayName by lazy { app.label as String }
        override val identifierName = ComponentKey(app.componentName, app.user).toString()
        override val drawable get() = app.getIcon(0)!!

        override fun toCustomEntry() = IconPackManager.CustomIconEntry("", ComponentKey(app.componentName, app.user).toString())
    }
}
