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

import android.os.Handler
import android.util.Log
import com.google.android.apps.nexuslauncher.smartspace.ISmartspace
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceDataContainer

class SmartspacePixelBridge(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller), ISmartspace, Runnable {

    private val smartspaceController = SmartspaceController.get(controller.context)
    private val handler = Handler()
    private var data: SmartspaceDataContainer? = null
    private var ds = false

    override fun startListening() {
        super.startListening()

        updateData(null, null)
        smartspaceController.da(this)
    }

    override fun stopListening() {
        super.stopListening()
        smartspaceController.da(null)
    }

    override fun onGsaChanged() {
        ds = smartspaceController.cY()
        if (data != null) {
            cr(data)
        } else {
            Log.d("SmartspacePixelBridge", "onGsaChanged but no data present")
        }
    }

    override fun cr(data: SmartspaceDataContainer?) {
        this.data = data?.also { initListeners(it) }
    }

    private fun initListeners(e: SmartspaceDataContainer) {
        val weatherData: LawnchairSmartspaceController.WeatherData? = if (e.isWeatherAvailable) {
            SmartspaceDataWidget.parseWeatherData(e.dO.icon, e.dO.title)
        } else {
            null
        }
        val cardData: LawnchairSmartspaceController.CardData? = if (e.cS()) {
            val dp = e.dP
            LawnchairSmartspaceController.CardData(dp.icon, dp.title, dp.cx(true), dp.cy(), dp.cx(false))
        } else {
            null
        }

        handler.removeCallbacks(this)
        if (e.cS() && e.dP.cv()) {
            val cw = e.dP.cw()
            var min = 61000L - System.currentTimeMillis() % 60000L
            if (cw > 0L) {
                min = Math.min(min, cw)
            }
            handler.postDelayed(this, min)
        }

        updateData(weatherData, cardData)
    }

    override fun run() {
        data?.let { initListeners(it) }
    }
}
