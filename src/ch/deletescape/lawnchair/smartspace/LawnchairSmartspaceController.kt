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
    private val providerPref = Utilities.getLawnchairPrefs(launcher)::weatherProvider
    private val enableEvents = Utilities.getLawnchairPrefs(launcher)::smartspaceEvents
    private var dataProvider = BlankDataProvider(this) as DataProvider

    init {
        onProviderChanged()
    }

    private fun updateData(weather: WeatherData?, card: CardData?) {
        originalSmartspaceData = DataContainer(weather, card)
        smartspaceData = DataContainer(weather, if (enableEvents.get()) card else null)
        notifyListeners()
    }

    fun updateData() {
        updateData(originalSmartspaceData.weather, originalSmartspaceData.card)
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
            if (providerPref.get() != dataProvider::class.java.name) {
                dataProvider.onDestroy()
                dataProvider = createDataProvider()
                runOnUiWorkerThread { providerPref.set(dataProvider::class.java.name) }
                providerPref.set(dataProvider::class.java.name)
            }
        }
    }

    private fun createDataProvider(): DataProvider {
        return try {
            (Class.forName(providerPref.get()).getConstructor(LawnchairSmartspaceController::class.java)
                    .newInstance(this) as DataProvider).apply {
                runOnMainThread(::performSetup)
                waitForSetup()
            }
        } catch (t: Throwable) {
            Log.d("LSC", "couldn't create weather provider", t)
            BlankDataProvider(this)
        }
    }

    abstract class DataProvider(val controller: LawnchairSmartspaceController) {

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
            controller.updateData(weather, card)
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
