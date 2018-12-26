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

import android.support.annotation.Keep
import android.text.TextUtils
import ch.deletescape.lawnchair.util.Temperature

@Keep
class FakeDataProvider(controller: LawnchairSmartspaceController) : LawnchairSmartspaceController.DataProvider(controller) {

    private val iconProvider = WeatherIconProvider(controller.context)
    private val weather = LawnchairSmartspaceController.WeatherData(iconProvider.getIcon("-1"),
            Temperature(0, Temperature.Unit.Celsius), "")
    private val card = LawnchairSmartspaceController.CardData(iconProvider.getIcon("-1"),
            "Title", TextUtils.TruncateAt.END, "Subtitle", TextUtils.TruncateAt.END)

    init {
        updateData(weather, card)
    }
}
