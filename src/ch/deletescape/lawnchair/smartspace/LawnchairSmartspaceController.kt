package ch.deletescape.lawnchair.smartspace

import android.graphics.Bitmap
import android.support.annotation.Keep
import android.text.TextUtils
import android.util.Log
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import com.android.launcher3.Utilities
import java.util.concurrent.Semaphore

class LawnchairSmartspaceController(val launcher: LawnchairLauncher) {

    var smartspaceData = DataContainer()
    private var weatherData: WeatherData? = null
    private var cardData: CardData? = null
    private val listeners = ArrayList<Listener>()
    private val weatherProviderPref = Utilities.getLawnchairPrefs(launcher)::weatherProvider
    private val eventProviderPref = Utilities.getLawnchairPrefs(launcher)::eventProvider
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
            }
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

    data class DataContainer(val weather: WeatherData? = null, val card: CardData? = null) {

        val isDoubleLine get() = isCardAvailable
        val isWeatherAvailable get() = weather != null
        val isCardAvailable get() = card != null
    }

    data class WeatherData(val icon: Bitmap, val temperature: Int, val isMetric: Boolean) {

        val title get() = "$temperature°$unit"
        val unit get() = if (isMetric) "C" else "F"
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
