package app.lawnchair.smartspace.provider

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import app.lawnchair.BlankActivity
import app.lawnchair.HeadlessWidgetsManager
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.Temperature
import app.lawnchair.util.pendingIntent
import app.lawnchair.util.recursiveChildren
import com.android.launcher3.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SmartspaceWidgetReader(context: Context) : SmartspaceDataSource(
    context, R.string.smartspace_weather, { smartspaceAagWidget }
) {
    override val isAvailable: Boolean

    override val disabledTargets = listOf(dummyTarget)
    private val widget: HeadlessWidgetsManager.Widget?
    override val internalTargets: Flow<List<SmartspaceTarget>>

    init {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = appWidgetManager.getInstalledProvidersForPackage(GSA_PACKAGE, null)
            .firstOrNull { it.provider.className == WIDGET_CLASS_NAME }
        isAvailable = provider != null
        widget = provider?.let {
            val widgetsManager = HeadlessWidgetsManager.INSTANCE.get(context)
            widgetsManager.getWidget(provider, "smartspaceWidgetId")
        }
        internalTargets = widget?.updates?.map(this::extractWidgetLayout) ?: flowOf(disabledTargets)
    }

    override suspend fun requiresSetup() = widget?.isBound == false

    override suspend fun startSetup(activity: Activity) {
        val intent = widget?.getBindIntent() ?: return
        BlankActivity.startBlankActivityForResult(activity, intent)
    }

    private fun extractWidgetLayout(appWidgetHostView: ViewGroup): List<SmartspaceTarget> {
        val children = appWidgetHostView.recursiveChildren.filter { it.isVisible }
        val texts = children.filterIsInstance<TextView>().filter { !it.text.isNullOrEmpty() }.toList()
        val images = children.filterIsInstance<ImageView>().toList()
        var weatherIconView: ImageView? = null
        var cardIconView: ImageView? = null
        var title: TextView? = null
        var subtitle: TextView? = null
        var subtitle2: TextView? = null
        var temperatureText: TextView? = null
        if (texts.isEmpty()) return listOf(dummyTarget)
        if (images.isNotEmpty()) {
            weatherIconView = images.last()
            temperatureText = texts.last()
        }
        if (images.size > 1 && texts.size > 2) {
            cardIconView = images.first()
            title = texts[0]
            subtitle = texts[1]
            if (texts.size > 3) {
                subtitle2 = texts[2]
            }
        }
        return parseData(extractBitmap(weatherIconView), temperatureText, extractBitmap(cardIconView), title, subtitle, subtitle2)
    }

    private fun parseData(
        weatherIcon: Bitmap?, temperature: TextView?,
        cardIcon: Bitmap?, title: TextView?, subtitle: TextView?, subtitle2: TextView?
    ): List<SmartspaceTarget> {
        val weather = parseWeatherData(weatherIcon, temperature) ?: dummyTarget
        val card = if (cardIcon != null && title != null && subtitle != null) {
            val pendingIntent = (title.parent.parent.parent as? View)?.pendingIntent
            val ttl = title.text.toString() + if (subtitle2 != null) subtitle.text.toString() else ""
            val sub = subtitle2 ?: subtitle
            SmartspaceTarget(
                id = "smartspaceWidgetCard",
                headerAction = SmartspaceAction(
                    id = "smartspaceWidgetCardAction",
                    icon = Icon.createWithBitmap(cardIcon),
                    title = ttl,
                    subtitle = sub.text,
                    pendingIntent = pendingIntent
                ),
                score = SmartspaceScores.SCORE_CALENDAR,
                featureType = SmartspaceTarget.FeatureType.FEATURE_CALENDAR
            )
        } else {
            null
        }
        return listOfNotNull(card, weather)
    }

    private fun parseWeatherData(weatherIcon: Bitmap?, temperatureText: TextView?): SmartspaceTarget? {
        val temperature = temperatureText?.text?.toString()
        val pendingIntent = (temperatureText?.parent as? View)?.pendingIntent
        val weatherData = parseWeatherData(
            weatherIcon, temperature, pendingIntent
        ) ?: return null
        return SmartspaceTarget(
            id = "smartspaceWidgetWeather",
            headerAction = SmartspaceAction(
                id = "smartspaceWidgetWeatherAction",
                icon = weatherIcon?.let { Icon.createWithBitmap(it) },
                title = "",
                subtitle = weatherData.getTitle(),
                pendingIntent = weatherData.pendingIntent
            ),
            score = SmartspaceScores.SCORE_WEATHER,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER
        )
    }

    private fun extractBitmap(imageView: ImageView?): Bitmap? {
        return (imageView?.drawable as? BitmapDrawable)?.bitmap
    }

    data class WeatherData(
        val icon: Bitmap,
        private val temperature: Temperature,
        val forecastUrl: String? = "https://www.google.com/search?q=weather",
        val forecastIntent: Intent? = null,
        val pendingIntent: PendingIntent? = null
    ) {

        fun getTitle(unit: Temperature.Unit = temperature.unit): String {
            return "${temperature.inUnit(unit)}${unit.suffix}"
        }
    }

    companion object {
        private const val GSA_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val WIDGET_CLASS_NAME = "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"

        private val dummyTarget = SmartspaceTarget(
            id = "dummyTarget",
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER
        )

        fun parseWeatherData(weatherIcon: Bitmap?, temperature: String?, intent: PendingIntent? = null): WeatherData? {
            return if (weatherIcon != null && temperature != null) {
                try {
                    val value = temperature.substring(0, temperature.indexOfFirst { (it < '0' || it > '9') && it != '-' }).toInt()
                    WeatherData(weatherIcon, Temperature(value, when {
                        temperature.contains("C") -> Temperature.Unit.Celsius
                        temperature.contains("F") -> Temperature.Unit.Fahrenheit
                        temperature.contains("K") -> Temperature.Unit.Kelvin
                        else -> throw IllegalArgumentException("only supports C, F and K")
                    }), pendingIntent = intent)
                } catch (_: NumberFormatException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            } else {
                null
            }
        }
    }
}
