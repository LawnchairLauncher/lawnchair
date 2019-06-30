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

import android.app.PendingIntent
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.Keep
import android.text.TextUtils
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.util.PackageManagerHelper
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class LawnchairSmartspaceController(val context: Context) {

    var smartspaceData = DataContainer()
    private var weatherData: WeatherData? = null
    private var cardData: CardData? = null
    private val listeners = ArrayList<Listener>()
    private val weatherProviderPref = Utilities.getLawnchairPrefs(context)::weatherProvider
    private val eventProvidersPref = context.lawnchairPrefs.eventProviders
    private var weatherDataProvider = BlankDataProvider(this) as DataProvider
    private val eventDataProviders = mutableListOf<DataProvider>()
    private val eventDataMap = mutableMapOf<DataProvider, CardData?>()

    init {
        onProviderChanged()
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
        smartspaceData = DataContainer(weather, card)
        notifyListeners()
    }

    private fun forceUpdate() {
        updateData(weatherData, eventDataProviders
                .asSequence()
                .mapNotNull { eventDataMap[it] }
                .firstOrNull())
    }

    private fun notifyListeners() {
        runOnMainThread {
            listeners.forEach { it.onDataUpdated(smartspaceData) }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onDataUpdated(smartspaceData)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun onProviderChanged() {
        runOnUiWorkerThread {
            val weatherClass = weatherProviderPref.get()
            val eventClasses = eventProvidersPref.getAll()
            if (weatherClass == weatherDataProvider::class.java.name
                && eventClasses == eventDataProviders.map { it::class.java.name }) {
                runOnMainThread(::forceUpdate)
                return@runOnUiWorkerThread
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
                it.onDestroy()
            }

            weatherProviderPref.set(weatherDataProvider::class.java.name)
            eventProvidersPref.setAll(eventDataProviders.map { it::class.java.name })

            runOnMainThread {
                needsUpdate.forEach { it.forceUpdate() }
                forceUpdate()
            }
        }
    }

    fun updateWeatherData() {
        runOnMainThread {
            weatherDataProvider.forceUpdate()
        }
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

    fun openEvent(v: View) {
        val data = cardData ?: return
        val launcher = Launcher.getLauncher(v.context)
        if (data.pendingIntent != null) {
            val opts = launcher.getActivityLaunchOptionsAsBundle(v)
            launcher.startIntentSender(
                    data.pendingIntent.intentSender, null,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    Intent.FLAG_ACTIVITY_NEW_TASK, 0, opts)
        }
    }

    private fun createDataProvider(className: String): DataProvider {
        return try {
            (Class.forName(className).getConstructor(LawnchairSmartspaceController::class.java)
                    .newInstance(this) as DataProvider).apply {
                runOnMainThread(::performSetup)
                waitForSetup()
            }
        } catch (t: Throwable) {
            Log.d("LSC", "couldn't create provider", t)
            BlankDataProvider(this)
        }
    }

    abstract class DataProvider(val controller: LawnchairSmartspaceController) {
        private var waiter: Semaphore? = Semaphore(0)

        var weatherUpdateListener: ((WeatherData?) -> Unit)? = null
        var cardUpdateListener: ((DataProvider, CardData?) -> Unit)? = null

        private var currentData: DataContainer? = null

        open fun performSetup() {
            onSetupComplete()
        }

        protected fun onSetupComplete() {
            waiter?.release()
        }

        @Synchronized
        open fun waitForSetup() {
            waiter?.run {
                acquireUninterruptibly()
                release()
                waiter = null
            }
        }

        open fun onDestroy() {
            weatherUpdateListener = null
            cardUpdateListener = null
        }

        fun updateData(weather: WeatherData?, card: CardData?) {
            currentData = DataContainer(weather, card)
            weatherUpdateListener?.invoke(weather)
            cardUpdateListener?.invoke(this, card)
        }

        open fun forceUpdate() {
            if (currentData != null) {
                updateData(currentData?.weather, currentData?.card)
            }
        }
    }

    abstract class PeriodicDataProvider(controller: LawnchairSmartspaceController) : DataProvider(controller) {

        private val handlerThread = HandlerThread(this::class.java.simpleName).apply { if (!isAlive) start() }
        private val handler = Handler(handlerThread.looper)
        private val update = ::periodicUpdate

        open val timeout = TimeUnit.MINUTES.toMillis(30)

        override fun performSetup() {
            super.performSetup()
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

        override fun onDestroy() {
            super.onDestroy()
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

    data class DataContainer(val weather: WeatherData? = null, val card: CardData? = null) {

        val isDoubleLine get() = isCardAvailable
        val isWeatherAvailable get() = weather != null
        val isCardAvailable get() = card != null
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

    data class CardData(val icon: Bitmap,
                        val title: String, val titleEllipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END,
                        val subtitle: String, val subtitleEllipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END,
                        val pendingIntent: PendingIntent? = null)

    interface Listener {

        fun onDataUpdated(data: DataContainer)
    }
}

@Keep
class BlankDataProvider(controller: LawnchairSmartspaceController) : LawnchairSmartspaceController.DataProvider(controller) {

    override fun performSetup() {
        super.performSetup()

        updateData(null, null)
    }
}
