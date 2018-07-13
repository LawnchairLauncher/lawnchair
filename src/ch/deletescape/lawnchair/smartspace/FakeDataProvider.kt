package ch.deletescape.lawnchair.smartspace

import android.support.annotation.Keep
import android.text.TextUtils

@Keep
class FakeDataProvider(controller: LawnchairSmartspaceController) : LawnchairSmartspaceController.DataProvider(controller) {

    private val iconProvider = WeatherIconProvider(controller.context)
    private val weather = LawnchairSmartspaceController.WeatherData(iconProvider.getIcon("-1"), 0, true, "")
    private val card = LawnchairSmartspaceController.CardData(iconProvider.getIcon("-1"),
            "Title", TextUtils.TruncateAt.END, "Subtitle", TextUtils.TruncateAt.END)

    init {
        updateData(weather, card)
    }
}
