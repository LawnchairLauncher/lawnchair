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

package ch.deletescape.lawnchair.smartspace

import android.app.Notification
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.support.annotation.Keep
import android.text.TextUtils
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import ch.deletescape.lawnchair.settings.ui.SettingsActivity.NOTIFICATION_BADGING
import ch.deletescape.lawnchair.settings.ui.SettingsActivity.SubSettingsFragment.CONTENT_RES_ID
import ch.deletescape.lawnchair.settings.ui.SettingsActivity.SubSettingsFragment.TITLE
import ch.deletescape.lawnchair.util.Temperature
import ch.deletescape.lawnchair.util.hasFlag
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.PackageManagerHelper
import java.util.concurrent.TimeUnit

class LawnchairSmartspaceController(val context: Context) {

    private var weatherData: WeatherData? = null
    private var cardData: CardData? = null
    private val listeners = ArrayList<Listener>()
    private val weatherProviderPref = Utilities.getLawnchairPrefs(context)::weatherProvider
    private val eventProvidersPref = context.lawnchairPrefs.eventProviders
    private var weatherDataProvider = BlankDataProvider(this) as DataProvider
    private val eventDataProviders = mutableListOf<DataProvider>()
    private val eventDataMap = mutableMapOf<DataProvider, CardData?>()

    private val stockProviderClasses = listOf(
            OnboardingProvider::class.java)
    private val stockProviders = mutableListOf<DataProvider>()

    var requiresSetup = false

    init {
        onProviderChanged()
        initStockProviders()
    }

    fun startSetup(onFinish: () -> Unit) {
        if (!requiresSetup) {
            onFinish()
            return
        }

        val provider = (listOf(weatherDataProvider) + eventDataProviders)
                .firstOrNull { !it.listening && it.requiresSetup() }

        if (provider != null) {
            provider.startSetup { success ->
                if (success) {
                    provider.startListening()
                    provider.forceUpdate()
                    forceUpdate()
                } else {
                    if (weatherDataProvider == provider) {
                        weatherProviderPref.set(BlankDataProvider::class.java.name)
                    }
                    if (eventDataProviders.contains(provider)) {
                        eventProvidersPref.setAll(eventDataProviders
                                                          .filter { it != provider }
                                                          .map { it::class.java.name })
                    }
                    onProviderChanged()
                }
                startSetup(onFinish)
            }
        } else {
            requiresSetup = false
            onFinish()
        }
    }

    private fun updateWeatherData(weather: WeatherData?) {
        updateData(weather, cardData)
    }

    private fun updateCardData(provider: DataProvider, card: CardData?) {
        eventDataMap[provider] = card
        forceUpdate()
    }

    private fun updateData(weather: WeatherData?, card: CardData?) {
        weatherData = weather
        cardData = card
        notifyListeners()
    }

    fun forceUpdate() {
        val allProviders = stockProviders.asSequence() + eventDataProviders.asSequence()
        val eventData = allProviders
                .mapNotNull { eventDataMap[it] }
                .firstOrNull()
        updateData(weatherData, eventData)
    }

    fun forceUpdateWeather() {
        weatherDataProvider.forceUpdate()
    }

    private fun notifyListeners() {
        listeners.forEach { it.onDataUpdated(weatherData, cardData) }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onDataUpdated(weatherData, cardData)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun onProviderChanged() {
        val weatherClass = weatherProviderPref.get()
        val eventClasses = eventProvidersPref.getAll()
        if (weatherClass == weatherDataProvider::class.java.name
            && eventClasses == eventDataProviders.map { it::class.java.name }) {
            forceUpdate()
            return
        }

        val activeProviders = eventDataProviders + weatherDataProvider
        val providerCache = activeProviders
                .associateByTo(mutableMapOf()) { it::class.java.name }
        val getProvider = { name: String ->
            providerCache.getOrPut(name) { createDataProvider(name) }
        }

        // Load all providers
        weatherDataProvider = getProvider(weatherClass)
        weatherDataProvider.weatherUpdateListener = ::updateWeatherData
        eventDataProviders.clear()
        eventClasses
                .map { getProvider(it) }
                .filterTo(eventDataProviders) { it !is BlankDataProvider }
                .forEach { it.cardUpdateListener = ::updateCardData }

        val allProviders = providerCache.values.toSet()
        val newProviders = setOf(weatherDataProvider) + eventDataProviders
        val needsDestroy = allProviders - newProviders
        val needsUpdate = newProviders - activeProviders

        needsDestroy.forEach {
            eventDataMap.remove(it)
            if (it.listening) {
                it.stopListening()
            }
        }

        weatherProviderPref.set(weatherDataProvider::class.java.name)
        eventProvidersPref.setAll(eventDataProviders.map { it::class.java.name })

        needsUpdate.forEach {
            if (!it.requiresSetup()) {
                it.startListening()
                it.forceUpdate()
            }
        }
        requiresSetup = newProviders.any { !it.listening && it.requiresSetup() }
        forceUpdate()
    }

    private fun initStockProviders() {
        val providers = mutableListOf<DataProvider>()
        stockProviderClasses
                .map { createDataProvider(it.name) }
                .filterTo(providers) { it !is BlankDataProvider }
                .forEach { it.cardUpdateListener = ::updateCardData }

        stockProviders.addAll(providers)
        providers.forEach { it.forceUpdate() }
        forceUpdate()
    }

    fun updateWeatherData() {
        weatherDataProvider.forceUpdate()
    }

    fun openWeather(v: View) {
        val data = weatherData ?: return
        val launcher = Launcher.getLauncher(v.context)
        if (data.pendingIntent != null) {
            val opts = launcher.getActivityLaunchOptionsAsBundle(v)
            launcher.startIntentSender(
                    data.pendingIntent.intentSender, null,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    Intent.FLAG_ACTIVITY_NEW_TASK, 0, opts)
        } else if (data.forecastIntent != null) {
            launcher.startActivitySafely(v, data.forecastIntent, null)
        } else if (PackageManagerHelper.isAppEnabled(launcher.packageManager, "com.google.android.googlequicksearchbox", 0)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("dynact://velour/weather/ProxyActivity")
            intent.component = ComponentName("com.google.android.googlequicksearchbox",
                    "com.google.android.apps.gsa.velour.DynamicActivityTrampoline")
            launcher.startActivitySafely(v, intent, null)
        } else {
            Utilities.openURLinBrowser(launcher, data.forecastUrl,
                    launcher.getViewBounds(v), launcher.getActivityLaunchOptions(v).toBundle())
        }
    }

    private fun createDataProvider(className: String): DataProvider {
        return try {
            (Class.forName(className).getConstructor(LawnchairSmartspaceController::class.java)
                    .newInstance(this) as DataProvider)
        } catch (t: Throwable) {
            Log.d("LSC", "couldn't create provider", t)
            BlankDataProvider(this)
        }
    }

    abstract class DataProvider(val controller: LawnchairSmartspaceController) {
        var listening = false
            private set

        var weatherUpdateListener: ((WeatherData?) -> Unit)? = null
        var cardUpdateListener: ((DataProvider, CardData?) -> Unit)? = null

        private var currentWeather: WeatherData? = null
        private var currentCard: CardData? = null

        protected val context = controller.context
        protected val resources = context.resources

        open fun requiresSetup() = false

        open fun startSetup(onFinish: (Boolean) -> Unit) {
            onFinish(true)
        }

        open fun startListening() {
            listening = true
        }

        open fun stopListening() {
            listening = false
            weatherUpdateListener = null
            cardUpdateListener = null
        }

        fun updateData(weather: WeatherData?, card: CardData?) {
            currentWeather = weather
            currentCard = card
            weatherUpdateListener?.invoke(weather)
            cardUpdateListener?.invoke(this, card)
        }

        open fun forceUpdate() {
            if (currentWeather != null || currentCard != null) {
                updateData(currentWeather, currentCard)
            }
        }

        protected fun getApp(name: String): CharSequence {
            val pm = controller.context.packageManager
            try {
                return pm.getApplicationLabel(
                        pm.getApplicationInfo(name, PackageManager.GET_META_DATA))
            } catch (ignored: PackageManager.NameNotFoundException) {
            }

            return name
        }

        protected fun getApp(sbn: StatusBarNotification): CharSequence {
            val context = controller.context
            val subName = sbn.notification.extras.getString(EXTRA_SUBSTITUTE_APP_NAME)
            if (subName != null) {
                if (context.checkPackagePermission(sbn.packageName, PERM_SUBSTITUTE_APP_NAME)) {
                    return subName
                }
            }
            return getApp(sbn.packageName)
        }

        companion object {

            private const val PERM_SUBSTITUTE_APP_NAME = "android.permission.SUBSTITUTE_NOTIFICATION_APP_NAME"
            private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"
        }
    }

    abstract class PeriodicDataProvider(controller: LawnchairSmartspaceController) : DataProvider(controller) {

        private val handlerThread = HandlerThread(this::class.java.simpleName).apply { if (!isAlive) start() }
        private val handler = Handler(handlerThread.looper)
        private val update = ::periodicUpdate

        open val timeout = TimeUnit.MINUTES.toMillis(30)

        override fun startListening() {
            super.startListening()
            handler.post(update)
        }

        private fun periodicUpdate() {
            try {
                updateData()
            } catch (e: Exception) {
                Log.d("PeriodicDataProvider", "failed to update data", e)
            }
            handler.postDelayed(update, timeout)
        }

        override fun stopListening() {
            super.stopListening()
            handlerThread.quit()
        }

        protected fun updateNow() {
            handler.removeCallbacks(update)
            handler.post(update)
        }

        open fun updateData() {
            updateData(queryWeatherData(), queryCardData())
        }

        open fun queryWeatherData(): WeatherData? {
            return null
        }

        open fun queryCardData() : CardData? {
            return null
        }

        override fun forceUpdate() {
            super.forceUpdate()
            updateNow()
        }
    }

    abstract class NotificationBasedDataProvider(controller: LawnchairSmartspaceController) :
            DataProvider(controller) {

        override fun startSetup(onFinish: (Boolean) -> Unit) {
            if (checkNotificationAccess()) {
                onFinish(true)
                return
            }

            val context = controller.context
            val providerName = getDisplayName(this::class.java.name)
            val intent: Intent
            val msg: String
            if (Utilities.ATLEAST_OREO) {
                intent = Intent(context, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY, "pref_icon_badging")
                        .putExtra(TITLE, context.getString(R.string.general_pref_title))
                        .putExtra(CONTENT_RES_ID, R.xml.lawnchair_desktop_preferences)
                msg = context.getString(R.string.event_provider_missing_notification_dots,
                                        context.getString(providerName))
            } else {
                val cn = ComponentName(context, NotificationListener::class.java)
                intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(":settings:fragment_args_key", cn.flattenToString())
                msg = context.getString(R.string.event_provider_missing_notification_access,
                                        context.getString(providerName),
                                        context.getString(R.string.derived_app_name))
            }
            BlankActivity.startActivityWithDialog(
                    context, intent, 1030,
                    context.getString(R.string.title_missing_notification_access),
                    msg,
                    context.getString(R.string.title_change_settings)) {
                onFinish(checkNotificationAccess())
            }
        }

        private fun checkNotificationAccess(): Boolean {
            val context = controller.context
            val enabledListeners = Settings.Secure.getString(
                    context.contentResolver, "enabled_notification_listeners")
            val myListener = ComponentName(context, NotificationListener::class.java)
            val listenerEnabled = enabledListeners?.let {
                it.contains(myListener.flattenToString()) || it.contains(myListener.flattenToString())
            } ?: false
            val badgingEnabled = Settings.Secure.getInt(context.contentResolver, NOTIFICATION_BADGING, 1) == 1
            return listenerEnabled && badgingEnabled
        }

        override fun requiresSetup() = !checkNotificationAccess()
    }

    data class WeatherData(val icon: Bitmap,
                           private val temperature: Temperature,
                           val forecastUrl: String? = "https://www.google.com/search?q=weather",
                           val forecastIntent: Intent? = null,
                           val pendingIntent: PendingIntent? = null) {

        fun getTitle(unit: Temperature.Unit): String {
            return "${temperature.inUnit(unit)} ${unit.suffix}"
        }
    }

    data class CardData(val icon: Bitmap? = null,
                        val lines: List<Line>,
                        val onClickListener: View.OnClickListener? = null,
                        val forceSingleLine: Boolean = false) {

        constructor(icon: Bitmap? = null,
                    lines: List<Line>,
                    intent: PendingIntent? = null,
                    forceSingleLine: Boolean = false) :
                this(icon, lines, intent?.let { PendingIntentClickListener(it) }, forceSingleLine)

        constructor(icon: Bitmap? = null,
                    lines: List<Line>,
                    forceSingleLine: Boolean = false) :
                this(icon, lines, null as View.OnClickListener?, forceSingleLine)

        constructor(icon: Bitmap?,
                    title: CharSequence, titleEllipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END,
                    subtitle: CharSequence, subtitleEllipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END,
                    pendingIntent: PendingIntent? = null)
                : this(icon, listOf(Line(title, titleEllipsize), Line(subtitle, subtitleEllipsize)), pendingIntent)

        val isDoubleLine = !forceSingleLine && lines.size >= 2

        val title: CharSequence?
        val titleEllipsize: TextUtils.TruncateAt?

        val subtitle: CharSequence?
        val subtitleEllipsize: TextUtils.TruncateAt?

        init {
            if (lines.isEmpty()) {
                error("Can't create card with zero lines")
            }
            if (forceSingleLine) {
                title = TextUtils.join(" – ", lines.map { it.text })!!
                titleEllipsize = if (lines.size == 1) lines.first().ellipsize else TextUtils.TruncateAt.END
                subtitle = null
                subtitleEllipsize = null
            } else {
                title = lines.first().text
                titleEllipsize = lines.first().ellipsize
                subtitle = TextUtils.join(" – ", lines.subList(1, lines.size).map { it.text })!!
                subtitleEllipsize = if (lines.size == 2) lines[1].ellipsize else TextUtils.TruncateAt.END
            }
        }
    }

    open class PendingIntentClickListener(private val pendingIntent: PendingIntent?) : View.OnClickListener {

        override fun onClick(v: View) {
            if (pendingIntent == null) return
            val launcher = Launcher.getLauncher(v.context)
            val opts = launcher.getActivityLaunchOptionsAsBundle(v)
            try {
                launcher.startIntentSender(
                        pendingIntent.intentSender, null,
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                        Intent.FLAG_ACTIVITY_NEW_TASK, 0, opts)
            } catch (e: ActivityNotFoundException) {
                // ignored
            }
        }
    }

    class NotificationClickListener(sbn: StatusBarNotification)
        : PendingIntentClickListener(sbn.notification.contentIntent) {

        private val key = sbn.key
        private val autoCancel = sbn.notification.flags.hasFlag(Notification.FLAG_AUTO_CANCEL)

        override fun onClick(v: View) {
            super.onClick(v)
            if (autoCancel) {
                Launcher.getLauncher(v.context).popupDataProvider.cancelNotification(key)
            }
        }
    }

    data class Line @JvmOverloads constructor(
            val text: CharSequence,
            val ellipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END) {

        constructor(context: Context, textRes: Int) : this(context.getString(textRes))
    }

    interface Listener {

        fun onDataUpdated(weather: WeatherData?, card: CardData?)
    }

    companion object {

        private val displayNames = mapOf(
                Pair(BlankDataProvider::class.java.name, R.string.weather_provider_disabled),
                Pair(SmartspaceDataWidget::class.java.name, R.string.google_app),
                Pair(SmartspacePixelBridge::class.java.name, R.string.smartspace_provider_bridge),
                Pair(OWMWeatherDataProvider::class.java.name, R.string.weather_provider_owm),
                Pair(AccuWeatherDataProvider::class.java.name, R.string.weather_provider_accu),
                Pair(PEWeatherDataProvider::class.java.name, R.string.weather_provider_pe),
                Pair(OnePlusWeatherDataProvider::class.java.name, R.string.weather_provider_oneplus_weather),
                Pair(NowPlayingProvider::class.java.name, R.string.event_provider_now_playing),
                Pair(NotificationUnreadProvider::class.java.name, R.string.event_provider_unread_notifications),
                Pair(BatteryStatusProvider::class.java.name, R.string.battery_status),
                Pair(PersonalityProvider::class.java.name, R.string.personality_provider),
                Pair(OnboardingProvider::class.java.name, R.string.onbording),
                Pair(FakeDataProvider::class.java.name, R.string.weather_provider_testing))

        fun getDisplayName(providerName: String): Int {
            return displayNames[providerName] ?: error("No display name for provider $providerName")
        }

        fun getDisplayName(context: Context, providerName: String): String {
            return context.getString(getDisplayName(providerName))
        }
    }
}

@Keep
class BlankDataProvider(controller: LawnchairSmartspaceController) : LawnchairSmartspaceController.DataProvider(controller) {

    override fun startListening() {
        super.startListening()

        updateData(null, null)
    }
}
