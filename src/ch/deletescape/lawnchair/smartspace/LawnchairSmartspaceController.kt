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
    private var originalSmartspaceData = DataContainer()
    private val listeners = ArrayList<Listener>()
    private val weatherProviderPref = Utilities.getLawnchairPrefs(launcher)::weatherProvider
    private val eventProviderPref = Utilities.getLawnchairPrefs(launcher)::weatherProvider
    private var weatherDataProvider = BlankDataProvider(this, true,  true) as DataProvider
    private var eventDataProvider = weatherDataProvider

    init {
        onProviderChanged()
    }

    private fun updateData(weather: WeatherData?, card: CardData?) {
        originalSmartspaceData = DataContainer(weather, card)
        smartspaceData = DataContainer(weather, card)
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it.onDataUpdated(smartspaceData) }
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
            if (weatherProviderPref.get() != weatherDataProvider::class.java.name || eventProviderPref.get() != eventDataProvider::class.java.name) {
                weatherDataProvider.onDestroy()
                eventDataProvider.onDestroy()
                createDataProviders()
                runOnUiWorkerThread {
                    weatherProviderPref.set(weatherDataProvider::class.java.name)
                    eventProviderPref.set(eventDataProvider::class.java.name)
                }
                weatherProviderPref.set(weatherDataProvider::class.java.name)
                eventProviderPref.set(eventDataProvider::class.java.name)
            }
        }
    }

    private fun createDataProviders() {
        val singleProvider = weatherProviderPref.get() == eventProviderPref.get()
        weatherDataProvider = try {
            (Class.forName(weatherProviderPref.get()).getConstructor(LawnchairSmartspaceController::class.java)
                    .newInstance(this, true, singleProvider) as DataProvider).apply {
                runOnMainThread(::performSetup)
                waitForSetup()
            }
        } catch (t: Throwable) {
            Log.d("LSC", "couldn't create weather provider", t)
            BlankDataProvider(this)
        }
        eventDataProvider = if (singleProvider) {
            weatherDataProvider
        } else {
            try {
                (Class.forName(eventProviderPref.get()).getConstructor(LawnchairSmartspaceController::class.java)
                        .newInstance(this, singleProvider, true) as DataProvider).apply {
                    runOnMainThread(::performSetup)
                    waitForSetup()
                }
            } catch (t: Throwable) {
                Log.d("LSC", "couldn't create event provider", t)
                BlankDataProvider(this)
            }
        }
    }

    abstract class DataProvider(val controller: LawnchairSmartspaceController, val providesWeather: Boolean = false, val providesCards: Boolean = false) {
        private var waiter: Semaphore? = Semaphore(0)

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

        }

        fun updateData(weather: WeatherData?, card: CardData?) {
            controller.updateData(if (providesWeather) weather else controller.originalSmartspaceData.weather, if (providesCards) card else controller.originalSmartspaceData.card)
        }

        fun updateWeather(weather: WeatherData?){
            if (providesWeather) controller.updateData(weather, controller.originalSmartspaceData.card)
        }

        fun updateCard(card: CardData?) {
            if (providesCards) controller.updateData(controller.originalSmartspaceData.weather, card)
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
class BlankDataProvider(controller: LawnchairSmartspaceController, providesWeather: Boolean = false, providesCards: Boolean = false) : LawnchairSmartspaceController.DataProvider(controller, providesWeather = providesWeather, providesCards = providesCards) {

    override fun performSetup() {
        super.performSetup()

        updateData(null, null)
    }
}
