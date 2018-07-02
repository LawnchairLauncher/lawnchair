package ch.deletescape.lawnchair.smartspace

import android.graphics.Bitmap
import ch.deletescape.lawnchair.LawnchairLauncher
import com.android.launcher3.Utilities

class LawnchairSmartspaceController(val launcher: LawnchairLauncher) {

    var smartspaceData = DataContainer()
    private val listeners = ArrayList<Listener>()
    private val providerPref = Utilities.getLawnchairPrefs(launcher)::weatherProvider
    private var dataProvider = DataProvider(this)

    init {
        onProviderChanged()
    }

    fun updateData(data: DataContainer) {
        smartspaceData = data
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
        if (providerPref.get() != dataProvider::class.java.simpleName) {
            dataProvider.onDestroy()
            dataProvider = createDataProvider()
            providerPref.set(dataProvider::class.java.simpleName)
        }
    }

    private fun createDataProvider(): DataProvider {
        try {
            when (providerPref.get()) {
                "SmartspaceDataWidget" -> return SmartspaceDataWidget(this)
            }
        } catch (t: Throwable) {

        }
        return DataProvider(this)
    }

    open class DataProvider(val controller: LawnchairSmartspaceController) {

        open fun onDestroy() {

        }

        fun updateData(weatherIcon: Bitmap, temperature: Int, isMetric: Boolean) {
            controller.updateData(DataContainer(WeatherData(weatherIcon, temperature, isMetric)))
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

    data class CardData(val icon: Bitmap, val title: String, val subtitle: String)

    interface Listener {

        fun onDataUpdated(data: DataContainer)
    }
}
