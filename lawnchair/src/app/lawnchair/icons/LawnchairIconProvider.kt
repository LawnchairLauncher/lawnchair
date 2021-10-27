package app.lawnchair.icons

import android.content.*
import android.content.Intent.*
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.getSystemService
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.MultiSafeCloseable
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.ThemedIconDrawable
import com.android.launcher3.util.SafeCloseable
import java.util.function.Supplier

class LawnchairIconProvider @JvmOverloads constructor(
    private val context: Context,
    supportsIconTheme: Boolean = false
) : IconProvider(context, supportsIconTheme) {

    private val prefs = PreferenceManager.getInstance(context)
    private val iconPackPref = prefs.iconPackPackage
    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)
    private val iconPack get() = iconPackProvider.getIconPack(iconPackPref.get())?.apply { loadBlocking() }

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
            if (iconEntry == null) {
                iconEntry = iconPack.getCalendar(componentName)?.getIconEntry(getDay())
                themeData = themedIconMap[mCalendar.packageName]
                iconType = ICON_TYPE_CALENDAR
            }
            if (iconEntry == null) {
                iconEntry = iconPack.getIcon(componentName)
                val clock = iconEntry?.let { iconPack.getClock(it) }
                if (clock != null) {
                    themeData = themedIconMap[mClock.packageName]
                    iconType = ICON_TYPE_CLOCK
                } else {
                    themeData = themedIconMap[componentName.packageName]
                }
            }
        }
        val icon = iconEntry?.getDrawable(iconDpi, user)
        val td = themeData
        if (icon != null) {
            return if (td != null) td.wrapDrawable(icon, iconType) else icon
        }
        return super.getIconWithOverrides(packageName, component, user, iconDpi, fallback)
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
        return super.getSystemIconState() + ",pack:${iconPackPref.get()}"
    }

    override fun registerIconChangeListener(
        callback: IconChangeListener,
        handler: Handler
    ): SafeCloseable {
        return MultiSafeCloseable().apply {
            add(super.registerIconChangeListener(callback, handler))
            add(IconPackChangeReceiver(context, handler, callback))
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
}
