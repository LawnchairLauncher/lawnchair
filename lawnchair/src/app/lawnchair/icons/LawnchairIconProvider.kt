package app.lawnchair.icons

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_PACKAGE_ADDED
import android.content.Intent.ACTION_PACKAGE_CHANGED
import android.content.Intent.ACTION_PACKAGE_REMOVED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.Intent.ACTION_TIME_TICK
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageItemInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.text.TextUtils
import android.util.ArrayMap
import android.util.Log
import androidx.core.content.getSystemService
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.Constants.LAWNICONS_PACKAGE_NAME
import app.lawnchair.util.MultiSafeCloseable
import app.lawnchair.util.getPackageVersionCode
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.LauncherIconProvider.ATTR_DRAWABLE
import com.android.launcher3.icons.LauncherIconProvider.ATTR_PACKAGE
import com.android.launcher3.icons.LauncherIconProvider.TAG_ICON
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.SafeCloseable
import org.xmlpull.v1.XmlPullParser

class LawnchairIconProvider @JvmOverloads constructor(
    private val context: Context,
    supportsIconTheme: Boolean = false,
) : IconProvider(context) {

    private val prefs = PreferenceManager.getInstance(context)
    private val iconPackPref = prefs.iconPackPackage
    private val themedIconPackPref = prefs.themedIconPackPackage

    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)
    private val overrideRepo = IconOverrideRepository.INSTANCE.get(context)

    private val iconPack
        get() = iconPackProvider.getIconPack(iconPackPref.get())?.apply { loadBlocking() }
    private val themedIconPack
        get() = iconPackProvider.getIconPack(themedIconPackPref.get())?.apply { loadBlocking() }

    private var isOlderLawniconsInstalled = context.packageManager.getPackageVersionCode(LAWNICONS_PACKAGE_NAME) in 1..3

    private var iconPackVersion = 0L

    private var themeMapName: String = ""
    private var mThemedIconMap: Map<String, ThemeData>? = null

    private val mThemeManager: ThemeManager? = null

    val themeMap: Map<String, ThemeData>
        get() {
            if (!context.isThemedIconsEnabled()) {
                mThemedIconMap = DISABLED_MAP
            }
            if (mThemedIconMap == null) {
                mThemedIconMap = createThemedIconMap()
            }
            if (isOlderLawniconsInstalled) {
                themeMapName = themedIconPackPref.get()
                mThemedIconMap = createThemedIconMap()
            }
            if (themedIconPack != null && themeMapName != themedIconPack!!.packPackageName) {
                themeMapName = themedIconPack!!.packPackageName
                mThemedIconMap = createThemedIconMap()
            }
            return mThemedIconMap!!
        }
    private val supportsIconTheme get() = themeMap != DISABLED_MAP

    init {
        setIconThemeSupported(supportsIconTheme)
    }

    /**
     * Enables or disables icon theme support (Lawnchair)
     * @see com.android.launcher3.icons.LauncherIconProvider.setIconThemeSupported
     */
    fun setIconThemeSupported(isSupported: Boolean) {
        if (isSupported && isOlderLawniconsInstalled && FeatureFlags.USE_LOCAL_ICON_OVERRIDES.get()) {
            mThemedIconMap = null
        } else {
            mThemedIconMap = DISABLED_MAP
        }
    }

    private fun resolveIconEntry(componentName: ComponentName, user: UserHandle): IconEntry? {
        val componentKey = ComponentKey(componentName, user)
        // first look for user-overridden icon
        val overrideItem = overrideRepo.overridesMap[componentKey]
        if (overrideItem != null) {
            return overrideItem.toIconEntry()
        }

        val iconPack = this.iconPack ?: return null
        // then look for dynamic calendar
        val calendarEntry = iconPack.getCalendar(componentName)
        if (calendarEntry != null) {
            return calendarEntry
        }
        // finally, look for normal icon
        return iconPack.getIcon(componentName)
    }

    fun isThemeEnabled(): Boolean {
        return mThemedIconMap != DISABLED_MAP
    }

    override fun getThemeDataForPackage(packageName: String?): ThemeData? {
        return getThemedIconMap().get(packageName)
    }

    fun getThemedIconMap(): MutableMap<String, ThemeData> {
        if (mThemedIconMap != null) {
            return mThemedIconMap!!.toMutableMap() // Lawnchair-TODO: This feels cursed?
        }
        val map = ArrayMap<String, ThemeData>()
        val res = mContext.getResources()
        try {
            res.getXml(R.xml.grayscale_icon_map).use { parser ->
                val depth = parser.getDepth()
                var type: Int
                while ((parser.next().also { type = it }) != XmlPullParser.START_TAG &&
                    type != XmlPullParser.END_DOCUMENT
                    );
                while ((
                        (parser.next().also { type = it }) != XmlPullParser.END_TAG ||
                            parser.getDepth() > depth
                        ) &&
                    type != XmlPullParser.END_DOCUMENT
                ) {
                    if (type != XmlPullParser.START_TAG) {
                        continue
                    }
                    if (TAG_ICON == parser.getName()) {
                        val pkg = parser.getAttributeValue(null, ATTR_PACKAGE)
                        val iconId = parser.getAttributeResourceValue(
                            null,
                            ATTR_DRAWABLE,
                            0,
                        )
                        if (iconId != 0 && !TextUtils.isEmpty(pkg)) {
                            map.put(pkg, ThemeData(res, iconId))
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Unable to parse icon map", e)
        }
        mThemedIconMap = map
        return mThemedIconMap!!.toMutableMap() // Lawnchair-TODO: This feels cursed?
    }

    override fun getIcon(info: ComponentInfo?): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info))
    }

    override fun getIcon(info: ComponentInfo?, iconDpi: Int): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info, iconDpi))
    }

    override fun getIcon(info: ApplicationInfo?): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info))
    }

    override fun getIcon(info: ApplicationInfo?, iconDpi: Int): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info, iconDpi))
    }

    override fun getIcon(info: PackageItemInfo?, appInfo: ApplicationInfo?, iconDpi: Int): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info, appInfo, iconDpi))
    }

    override fun updateSystemState() {
        super.updateSystemState()
        mSystemState += "," + mThemeManager?.iconState?.toUniqueId()
    }

    override fun registerIconChangeListener(
        callback: IconChangeListener,
        handler: Handler,
    ): SafeCloseable {
        return MultiSafeCloseable().apply {
            add(super.registerIconChangeListener(callback, handler))
            add(IconPackChangeReceiver(context, handler, callback))
            add(LawniconsChangeReceiver(context, handler, callback))
        }
    }

    private inner class IconPackChangeReceiver(
        private val context: Context,
        private val handler: Handler,
        private val callback: IconChangeListener,
    ) : SafeCloseable {

        private var calendarAndClockChangeReceiver: CalendarAndClockChangeReceiver? = null
            set(value) {
                field?.close()
                field = value
            }
        private var iconState = mThemeManager?.iconState
        private val iconPackPref = PreferenceManager.getInstance(context).iconPackPackage
        private val themedIconPackPref = PreferenceManager.getInstance(context).themedIconPackPackage

        private val subscription = iconPackPref.subscribeChanges {
            val newState = mThemeManager?.iconState
            if (iconState != newState) {
                iconState = newState
                updateSystemState()
                recreateCalendarAndClockChangeReceiver()
            }
        }
        private val themedIconSubscription = themedIconPackPref.subscribeChanges {
            val newState = mThemeManager?.iconState
            if (iconState != newState) {
                iconState = newState
                updateSystemState()
                recreateCalendarAndClockChangeReceiver()
            }
        }

        init {
            recreateCalendarAndClockChangeReceiver()
        }

        private fun recreateCalendarAndClockChangeReceiver() {
            val iconPack = IconPackProvider.INSTANCE.get(context).getIconPack(iconPackPref.get())
            calendarAndClockChangeReceiver = if (iconPack != null) {
                CalendarAndClockChangeReceiver(context, handler, iconPack, callback)
            } else {
                null
            }
        }

        override fun close() {
            calendarAndClockChangeReceiver = null
            subscription.close()
            themedIconSubscription.close()
        }
    }

    private class CalendarAndClockChangeReceiver(
        private val context: Context,
        handler: Handler,
        private val iconPack: IconPack,
        private val callback: IconChangeListener,
    ) : BroadcastReceiver(),
        SafeCloseable {

        init {
            val filter = IntentFilter(ACTION_TIMEZONE_CHANGED)
            filter.addAction(ACTION_TIME_TICK)
            filter.addAction(ACTION_TIME_CHANGED)
            filter.addAction(ACTION_DATE_CHANGED)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TIMEZONE_CHANGED, ACTION_TIME_CHANGED, ACTION_TIME_TICK -> {
                    context.getSystemService<UserManager>()?.userProfiles?.forEach { user ->
                        iconPack.getClocks().forEach { componentName ->
                            callback.onAppIconChanged(
                                componentName.packageName,
                                user,
                            )
                        }
                    }
                }
                ACTION_DATE_CHANGED -> {
                    context.getSystemService<UserManager>()?.userProfiles?.forEach { user ->
                        iconPack.getCalendars().forEach { componentName ->
                            callback.onAppIconChanged(componentName.packageName, user)
                        }
                    }
                }
            }
        }

        override fun close() {
            context.unregisterReceiver(this)
        }
    }

    private inner class LawniconsChangeReceiver(
        private val context: Context,
        handler: Handler,
        private val callback: IconChangeListener,
    ) : BroadcastReceiver(),
        SafeCloseable {

        init {
            val filter = IntentFilter(ACTION_PACKAGE_ADDED)
            filter.addAction(ACTION_PACKAGE_CHANGED)
            filter.addAction(ACTION_PACKAGE_REMOVED)
            filter.addDataScheme("package")
            filter.addDataSchemeSpecificPart(themeMapName, 0)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (isThemeEnabled()) {
                setIconThemeSupported(true)
            }
            updateSystemState()
        }

        override fun close() {
            context.unregisterReceiver(this)
        }
    }

    private fun createThemedIconMap(): MutableMap<String, ThemeData> {
        val map = ArrayMap<String, ThemeData>()

        fun updateMapFromResources(resources: Resources, packageName: String) {
            try {
                @SuppressLint("DiscouragedApi")
                val xmlId = resources.getIdentifier("grayscale_icon_map", "xml", packageName)
                if (xmlId != 0) {
                    val parser = resources.getXml(xmlId)
                    val depth = parser.depth
                    var type: Int
                    while (
                        (parser.next().also { type = it } != XmlPullParser.END_TAG || parser.depth > depth) &&
                        type != XmlPullParser.END_DOCUMENT
                    ) {
                        if (type != XmlPullParser.START_TAG) continue
                        if (TAG_ICON == parser.name) {
                            val pkg = parser.getAttributeValue(null, ATTR_PACKAGE)
                            val iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0)
                            if (iconId != 0 && !pkg.isNullOrEmpty()) {
                                map[pkg] = ThemeData(resources, iconId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to parse icon map.", e)
            }
        }
        updateMapFromResources(
            resources = context.resources,
            packageName = context.packageName,
        )
        if (context.packageManager.isPackageInstalled(packageName = themeMapName)) {
            iconPackVersion = context.packageManager.getPackageVersionCode(themeMapName)
            updateMapFromResources(
                resources = context.packageManager.getResourcesForApplication(themeMapName),
                packageName = themeMapName,
            )
            if (isOlderLawniconsInstalled) {
                // updateMapWithDynamicIcons(context, map)
            }
        }

        return map
    }

    companion object {
        const val TAG = "LawnchairIconProvider"

        val DISABLED_MAP = emptyMap<String, ThemeData>()
    }
}
