package app.lawnchair.icons

import android.content.*
import android.content.Intent.*
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import android.util.Log
import androidx.core.content.getSystemService
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.Constants.LAWNICONS_PACKAGE_NAME
import app.lawnchair.util.MultiSafeCloseable
import app.lawnchair.util.getPackageVersionCode
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.ThemedIconDrawable
import com.android.launcher3.util.SafeCloseable
import org.xmlpull.v1.XmlPullParser
import java.util.function.Supplier

class LawnchairIconProvider @JvmOverloads constructor(
    private val context: Context,
    supportsIconTheme: Boolean = false
) : IconProvider(context, supportsIconTheme) {

    private val prefs = PreferenceManager.getInstance(context)
    private val iconPackPref = prefs.iconPackPackage
    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)
    private val iconPack get() = iconPackProvider.getIconPack(iconPackPref.get())?.apply { loadBlocking() }
    private var lawniconsVersion = 0L

    private var _themeMap: Map<ComponentName, ThemedIconDrawable.ThemeData>? = null
    private val themeMap: Map<ComponentName, ThemedIconDrawable.ThemeData>
        get() {
            if (_themeMap == null) {
                _themeMap = createThemedIconMap()
            }
            return _themeMap!!
        }
    val supportsIconTheme get() = themeMap != DISABLED_MAP

    init {
        setIconThemeSupported(supportsIconTheme)
    }

    override fun setIconThemeSupported(isSupported: Boolean) {
        lawniconsVersion =
            if (isSupported) context.packageManager.getPackageVersionCode(LAWNICONS_PACKAGE_NAME)
            else 0L
        _themeMap = if (isSupported) null else DISABLED_MAP
    }

    override fun getIconWithOverrides(
        packageName: String,
        component: String,
        user: UserHandle,
        iconDpi: Int,
        fallback: Supplier<Drawable>
    ): Drawable {
        val iconPack = this.iconPack
        val componentName = ComponentName(packageName, component)
        var iconEntry: IconEntry? = null

        var iconType = ICON_TYPE_DEFAULT
        var themeData: ThemedIconDrawable.ThemeData? = null
        if (iconPack != null) {
            // check if supports dynamic calendar
            val calendarEntry = iconPack.getCalendar(componentName)
            if (calendarEntry != null) {
                iconEntry = calendarEntry.getIconEntry(getDay())
                themeData = getThemeData(mCalendar.packageName, "")
                iconType = ICON_TYPE_CALENDAR
            }
            // doesn't support dynamic calendar
            if (iconEntry == null) {
                iconEntry = iconPack.getIcon(componentName)
                val clock = iconEntry?.let { iconPack.getClock(it) }
                when {
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
                        themeData = ThemedIconDrawable.ThemeData(context.resources, R.drawable.themed_icon_static_clock)
                    }
                    else -> {
                        // regular app
                        themeData = getThemeData(componentName)
                    }
                }
            }
        }
        val icon = iconEntry?.let { iconPackProvider.getDrawable(iconEntry, iconDpi, user) }
        val td = themeData
        if (icon != null) {
            return if (td != null) td.wrapDrawable(icon, iconType) else icon
        }
        return super.getIconWithOverrides(packageName, component, user, iconDpi, fallback)
    }

    override fun isThemeEnabled(): Boolean {
        return _themeMap != DISABLED_MAP
    }

    override fun getThemeData(componentName: ComponentName): ThemedIconDrawable.ThemeData? {
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
        return super.getSystemStateForPackage(systemState, packageName)
    }

    override fun getSystemIconState(): String {
        return super.getSystemIconState() + ",pack:${iconPackPref.get()},lawnicons:${lawniconsVersion}"
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
        private val subscription = iconPackPref.subscribeChanges {
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
        }
    }

    private class CalendarAndClockChangeReceiver(
        private val context: Context, handler: Handler,
        private val iconPack: IconPack,
        private val callback: IconChangeListener
    ) : BroadcastReceiver(), SafeCloseable {

        init {
            val filter = IntentFilter(ACTION_TIMEZONE_CHANGED)
            filter.addAction(ACTION_TIME_CHANGED)
            filter.addAction(ACTION_DATE_CHANGED)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TIMEZONE_CHANGED -> {
                    iconPack.getClocks().forEach { componentName ->
                        callback.onAppIconChanged(componentName.packageName, Process.myUserHandle())
                    }
                }
                ACTION_DATE_CHANGED, ACTION_TIME_CHANGED -> {
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
            filter.addDataSchemeSpecificPart(LAWNICONS_PACKAGE_NAME, 0)
            context.registerReceiver(this, filter, null, handler)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (isThemeEnabled) {
                setIconThemeSupported(true)
                callback.onSystemIconStateChanged(systemIconState)
            }
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
                            val cmp = parser.getAttributeValue(null, ATTR_COMPONENT) ?: ""
                            val iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0)
                            if (iconId != 0 && pkg.isNotEmpty()) {
                                map[ComponentName(pkg, cmp)] = ThemedIconDrawable.ThemeData(resources, iconId)
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

        if (context.packageManager.isPackageInstalled(packageName = LAWNICONS_PACKAGE_NAME)) {
            updateMapFromResources(
                resources = context.packageManager.getResourcesForApplication(LAWNICONS_PACKAGE_NAME),
                packageName = LAWNICONS_PACKAGE_NAME
            )
        }

        return map
    }

    companion object {
        const val TAG = "LawnchairIconProvider"

        val DISABLED_MAP = emptyMap<ComponentName, ThemedIconDrawable.ThemeData>()
    }
}
