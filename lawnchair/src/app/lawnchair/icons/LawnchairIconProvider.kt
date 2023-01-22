package app.lawnchair.icons

import android.content.*
import android.content.Intent.*
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import android.util.Log
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.core.content.getSystemService
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.Constants.LAWNICONS_PACKAGE_NAME
import app.lawnchair.util.MultiSafeCloseable
import app.lawnchair.util.getPackageVersionCode
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.ThemedIconDrawable
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.SafeCloseable
import org.xmlpull.v1.XmlPullParser
import java.util.function.Supplier


class LawnchairIconProvider @JvmOverloads constructor(
    private val context: Context,
    supportsIconTheme: Boolean = false
) : IconProvider(context, supportsIconTheme) {

    private val prefs = PreferenceManager.getInstance(context)
    private val iconPackPref = prefs.iconPackPackage
    private val themedIconPackPref = prefs.themedIconPackPackage
    private val drawerThemedIcons get()= prefs.drawerThemedIcons
    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)
    private val overrideRepo = IconOverrideRepository.INSTANCE.get(context)
    private val iconPack get() = iconPackProvider.getIconPack(iconPackPref.get())?.apply { loadBlocking() }
    private val themedIconPack get() = iconPackProvider.getIconPack(themedIconPackPref.get())?.apply { loadBlocking() }
    private var isOlderLawnIconsInstalled = context.packageManager.getPackageVersionCode(LAWNICONS_PACKAGE_NAME) in 1..3
    private var iconPackVersion = 0L
    private var _themeMapName: String = ""
    private var _themeMap: Map<ComponentName, ThemedIconDrawable.ThemeData>? = null
    private val themeMap: Map<ComponentName, ThemedIconDrawable.ThemeData>
        get() {
            if(drawerThemedIcons.get() && !(isOlderLawnIconsInstalled)){
                _themeMap = DISABLED_MAP
            }
            if (_themeMap == null) {
                _themeMap = createThemedIconMap()
            }
            if(isOlderLawnIconsInstalled && themedIconPackPref.get() == LAWNICONS_PACKAGE_NAME){
                _themeMapName = themedIconPackPref.get()
                _themeMap = createThemedIconMap()
            }
            if(themedIconPack != null && _themeMapName != themedIconPack!!.packPackageName){
                _themeMapName = themedIconPack!!.packPackageName
                _themeMap = createThemedIconMap()
            }
            return _themeMap!!
        }
    private val supportsIconTheme get() = themeMap != DISABLED_MAP

    init {
        setIconThemeSupported(supportsIconTheme)
    }

    override fun setIconThemeSupported(isSupported: Boolean) {
        _themeMap = if (isSupported && isOlderLawnIconsInstalled) null else DISABLED_MAP
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

    override fun getIconWithOverrides(
        packageName: String,
        component: String,
        user: UserHandle,
        iconDpi: Int,
        fallback: Supplier<Drawable>
    ): Drawable {
        val componentName = ComponentName(packageName, component)
        val iconEntry = resolveIconEntry(componentName, user)
        var resolvedEntry = iconEntry
        var iconType = ICON_TYPE_DEFAULT
        var themeData: ThemedIconDrawable.ThemeData? = null
        if (iconEntry != null) {
            val clock = iconPackProvider.getClockMetadata(iconEntry)
            when {
                iconEntry.type == IconType.Calendar -> {
                    resolvedEntry = iconEntry.resolveDynamicCalendar(getDay())
                    themeData = getThemeData(mCalendar.packageName, "")
                    iconType = ICON_TYPE_CALENDAR
                }
                !supportsIconTheme -> {
                    // theming is disabled, don't populate theme data
                }
                clock != null -> {
                    // the icon supports dynamic clock, use dynamic themed clock
                    themeData = getThemeData(mClock.packageName, "")
                    iconType = ICON_TYPE_CLOCK
                }
                packageName == mClock.packageName -> {
                    // is clock app but icon might not be adaptive, fallback to static themed clock
                    themeData = ThemedIconDrawable.ThemeData(context.resources, BuildConfig.APPLICATION_ID, R.drawable.themed_icon_static_clock)
                }
                packageName == mCalendar.packageName -> {
                    // calendar app, apply the dynamic calendar icon
                    themeData = getThemeData(mCalendar.packageName, "")
                    iconType = ICON_TYPE_CALENDAR
                }
                else -> {
                    // regular icon
                    themeData = getThemeData(componentName)
                }
            }
        }
        val icon = resolvedEntry?.let { iconPackProvider.getDrawable(it, iconDpi, user) }
        val td = themeData
        if (icon != null) return if (td != null) td.wrapDrawable(icon, iconType) else icon
        var defaultIcon =
            super.getIconWithOverrides(packageName, component, user, iconDpi, fallback)
        if (context.isThemedIconsEnabled() && defaultIcon is AdaptiveIconDrawable &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && defaultIcon.monochrome != null) {
            defaultIcon = defaultIcon.monochrome
            return if (td != null) td.wrapDrawable(defaultIcon, iconType) else {
                val themedColors = ThemedIconDrawable.getThemedColors(context)
                if (context.shouldTransparentBGIcons()) {
                    return defaultIcon.apply { setTint(themedColors[1]) }
                }
                CustomAdaptiveIconDrawable(ColorDrawable(themedColors[0]),
                    defaultIcon.apply { setTint(themedColors[1]) })
            }
        }
        return defaultIcon
    }

    override fun isThemeEnabled(): Boolean {
        return _themeMap != DISABLED_MAP
    }

    override fun getThemeData(componentName: ComponentName): ThemedIconDrawable.ThemeData? {
        val td = getDynamicIconsFromMap(context, themeMap, componentName)
        if(td != null){
            return td
        }
        return themeMap[componentName] ?: themeMap[ComponentName(componentName.packageName, "")]
    }

    override fun getIcon(info: ActivityInfo?): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info))
    }

    override fun getIcon(info: ActivityInfo?, iconDpi: Int): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info, iconDpi))
    }

    override fun getIcon(info: LauncherActivityInfo?, iconDpi: Int): Drawable {
        return CustomAdaptiveIconDrawable.wrapNonNull(super.getIcon(info, iconDpi))
    }

    override fun getSystemStateForPackage(systemState: String, packageName: String): String {
        return super.getSystemStateForPackage(systemState, packageName) + ",${isThemeEnabled}"
    }

    override fun getSystemIconState(): String {
        return super.getSystemIconState() + ",pack:${iconPackPref.get()}/${themedIconPackPref.get()},ver:${iconPackVersion}"
    }

    override fun registerIconChangeListener(
        callback: IconChangeListener,
        handler: Handler
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
        private val callback: IconChangeListener
    ) : SafeCloseable {

        private var calendarAndClockChangeReceiver: CalendarAndClockChangeReceiver? = null
            set(value) {
                field?.close()
                field = value
            }
        private var iconState = systemIconState
        private val iconPackPref = PreferenceManager.getInstance(context).iconPackPackage
        private val themedIconPackPref = PreferenceManager.getInstance(context).themedIconPackPackage

        private val subscription = iconPackPref.subscribeChanges {
            val newState = systemIconState
            if (iconState != newState) {
                iconState = newState
                callback.onSystemIconStateChanged(iconState)
                recreateCalendarAndClockChangeReceiver()
            }
        }
        private val themedIconSubscription = themedIconPackPref.subscribeChanges {
            val newState = systemIconState
            if (iconState != newState) {
                iconState = newState
                callback.onSystemIconStateChanged(iconState)
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
        private val context: Context, handler: Handler,
        private val iconPack: IconPack,
        private val callback: IconChangeListener
    ) : BroadcastReceiver(), SafeCloseable {

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
                                user
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
        private val context: Context, handler: Handler,
        private val callback: IconChangeListener
    ) : BroadcastReceiver(), SafeCloseable {

        init {
            val filter = IntentFilter(ACTION_PACKAGE_ADDED)
            filter.addAction(ACTION_PACKAGE_CHANGED)
            filter.addAction(ACTION_PACKAGE_REMOVED)
            filter.addDataScheme("package")
            filter.addDataSchemeSpecificPart(_themeMapName, 0)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (isThemeEnabled) {
                setIconThemeSupported(true)
            }
            callback.onSystemIconStateChanged(systemIconState)
        }

        override fun close() {
            context.unregisterReceiver(this)
        }
    }

    private fun createThemedIconMap(): MutableMap<ComponentName, ThemedIconDrawable.ThemeData> {
        val map = ArrayMap<ComponentName, ThemedIconDrawable.ThemeData>()

        fun updateMapFromResources(resources: Resources, packageName: String) {
            try {
                val xmlId = resources.getIdentifier(THEMED_ICON_MAP_FILE, "xml", packageName)
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
                            val cmp = parser.getAttributeValue(null, ATTR_COMPONENT).orEmpty()
                            val iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0)
                            if (iconId != 0 && pkg.isNotEmpty()) {
                                map[ComponentName(pkg, cmp)] = ThemedIconDrawable.ThemeData(resources, packageName, iconId)
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
            packageName = context.packageName
        )
        if (context.packageManager.isPackageInstalled(packageName = _themeMapName)) {
            iconPackVersion = context.packageManager.getPackageVersionCode(_themeMapName)
            updateMapFromResources(
                resources = context.packageManager.getResourcesForApplication(_themeMapName),
                packageName = _themeMapName
            )
            if(isOlderLawnIconsInstalled) {
                updateMapWithDynamicIcons(context, map)
            }
        }

        return map
    }

    companion object {
        const val TAG = "LawnchairIconProvider"

        val DISABLED_MAP = emptyMap<ComponentName, ThemedIconDrawable.ThemeData>()
    }
}
