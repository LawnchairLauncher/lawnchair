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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.Keep
import android.text.TextUtils
import android.util.Log
import android.view.View
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
    private val eventProviderPref = Utilities.getLawnchairPrefs(context)::eventProvider
    private var weatherDataProvider = BlankDataProvider(this) as DataProvider
    private var eventDataProvider = weatherDataProvider

    init {
        onProviderChanged()
    }

    private fun updateWeatherData(weather: WeatherData?) {
        updateData(weather, cardData)
    }

    private fun updateCardData(card: CardData?) {
        updateData(weatherData, card)
    }

    private fun updateData(weather: WeatherData?, card: CardData?) {
        weatherData = weather
        cardData = card
        smartspaceData = DataContainer(weather, card)
        notifyListeners()
    }

    private fun forceUpdate() {
        updateData(weatherData, cardData)
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
            val eventClass = eventProviderPref.get()
            if (weatherClass != weatherDataProvider::class.java.name || eventClass != eventDataProvider::class.java.name) {
                weatherDataProvider.onDestroy()
                eventDataProvider.onDestroy()
                weatherDataProvider = createDataProvider(weatherClass)
                weatherDataProvider.weatherUpdateListener = ::updateWeatherData
                eventDataProvider = if (weatherClass == eventClass) {
                    weatherDataProvider
                } else {
                    createDataProvider(eventClass)
                }
                eventDataProvider.cardUpdateListener = ::updateCardData
                runOnUiWorkerThread {
                    weatherProviderPref.set(weatherDataProvider::class.java.name)
                    eventProviderPref.set(eventDataProvider::class.java.name)
                }
                runOnMainThread {
                    weatherDataProvider.forceUpdate()
                    if (weatherClass != eventClass) {
                        eventDataProvider.forceUpdate()
                    }
                }
            } else {
                runOnMainThread(::forceUpdate)
            }
        }
    }

    fun openWeather(v: View) {
        if (weatherData == null) return
        val launcher = Launcher.getLauncher(v.context)
        if (PackageManagerHelper.isAppEnabled(launcher.packageManager, "com.google.android.googlequicksearchbox", 0)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("dynact://velour/weather/ProxyActivity")
            intent.component = ComponentName("com.google.android.googlequicksearchbox",
                    "com.google.android.apps.gsa.velour.DynamicActivityTrampoline")
            launcher.startActivitySafely(v, intent, null)
        } else {
            Utilities.openURLinBrowser(launcher, weatherData!!.forecastUrl,
                    launcher.getViewBounds(v), launcher.getActivityLaunchOptions(v).toBundle())
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
        var cardUpdateListener: ((CardData?) -> Unit)? = null

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
            cardUpdateListener?.invoke(card)
        }

        fun forceUpdate() {
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
    }

    data class DataContainer(val weather: WeatherData? = null, val card: CardData? = null) {

        val isDoubleLine get() = isCardAvailable
        val isWeatherAvailable get() = weather != null
        val isCardAvailable get() = card != null
    }

    data class WeatherData(val icon: Bitmap,
                           private val temperature: Temperature,
                           val forecastUrl: String = "https://www.google.com/search?q=weather") {

        fun getTitle(unit: Temperature.Unit): String {
            return "${temperature.inUnit(unit)} ${unit.suffix}"
        }
    }

    data class CardData(val icon: Bitmap,
                        val title: String, val titleEllipsize: TextUtils.TruncateAt?,
                        val subtitle: String, val subtitleEllipsize: TextUtils.TruncateAt?)

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
