/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.support.annotation.Keep
import ch.deletescape.lawnchair.dayOfYear
import ch.deletescape.lawnchair.hourOfDay
import com.android.launcher3.R
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

@Keep
class PersonalityProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller) {
    private val updateInterval = 60 * 1000
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            onUpdate()
        }
    }

    var time = currentTime()!!
    var randomIndex = 0
    val isMorning get() = time.hourOfDay in 5 until 9
    val isEvening get() = time.hourOfDay in 19 until 21
    val isNight get() = time.hourOfDay in 22 until 24 || time.hourOfDay in 0 until 4
    val morningGreeting get() = morningStrings[randomIndex % morningStrings.size]
    val eveningGreeting get() = eveningStrings[randomIndex % eveningStrings.size]
    val nightGreeting get() = nightStrings[randomIndex % nightStrings.size]

    private val morningStrings = controller.context.resources.getStringArray(R.array.greetings_morning)
    private val eveningStrings = controller.context.resources.getStringArray(R.array.greetings_evening)
    private val nightStrings = controller.context.resources.getStringArray(R.array.greetings_night)

    private val handler = Handler(Looper.getMainLooper())
    private val onUpdateRunnable = ::onUpdate

    private fun currentTime() = Calendar.getInstance()

    override fun startListening() {
        super.startListening()
        context.registerReceiver(
                timeReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                })
        onUpdate()
    }

    private fun onUpdate() {
        time = currentTime()
        randomIndex = abs(Random(time.dayOfYear).nextInt())
        updateData(null, getEventCard())

        val now = System.currentTimeMillis()
        handler.removeCallbacks(onUpdateRunnable)
        handler.postDelayed(onUpdateRunnable, updateInterval - now % updateInterval)
    }

    override fun stopListening() {
        super.stopListening()
        handler.removeCallbacks(onUpdateRunnable)
    }

    private fun getEventCard(): LawnchairSmartspaceController.CardData? {
        val lines = mutableListOf<LawnchairSmartspaceController.Line>()
        when {
            isMorning -> lines.add(LawnchairSmartspaceController.Line(morningGreeting))
            isEvening -> lines.add(LawnchairSmartspaceController.Line(eveningGreeting))
            isNight -> lines.add(LawnchairSmartspaceController.Line(nightGreeting))
            else -> return null
        }
        return LawnchairSmartspaceController.CardData(
                lines = lines,
                forceSingleLine = true)
    }
}