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
import android.support.annotation.Keep
import ch.deletescape.lawnchair.dayOfYear
import ch.deletescape.lawnchair.hourOfDay
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

@Keep
class PersonalityProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller) {
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            time = currentTime()
        }
    }

    var time = currentTime()!!
        set(value) {
            randomIndex = abs(Random(value.dayOfYear).nextInt())
            if (field.hourOfDay != value.hourOfDay) {
                field = value
                updateData(null, getEventCard())
            }
        }
    var randomIndex = 0
    val isMorning get() = time.hourOfDay in 5 until 9
    val isEvening get() = time.hourOfDay in 19 until 24 || time.hourOfDay == 0
    val morningGreeting get() = morningStrings[randomIndex % morningStrings.size]
    val eveningGreeting get() = eveningStrings[randomIndex % eveningStrings.size]

    private val morningStrings = controller.context.resources.getStringArray(R.array.greetings_morning)
    private val eveningStrings = controller.context.resources.getStringArray(R.array.greetings_evening)

    private fun currentTime() = Calendar.getInstance()

    override fun performSetup() {
        super.performSetup()
        context.registerReceiver(
                timeReceiver,
                IntentFilter(Intent.ACTION_DATE_CHANGED).apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    if (!Utilities.ATLEAST_NOUGAT) {
                        addAction(Intent.ACTION_TIME_TICK)
                    }
                })
        updateData(null, getEventCard())
    }

    private fun getEventCard(): LawnchairSmartspaceController.CardData? {
        val lines = mutableListOf<LawnchairSmartspaceController.Line>()
        when {
            isMorning -> lines.add(LawnchairSmartspaceController.Line(morningGreeting))
            isEvening -> lines.add(LawnchairSmartspaceController.Line(eveningGreeting))
            else -> return null
        }
        return LawnchairSmartspaceController.CardData(
                lines = lines,
                forceSingleLine = true)
    }
}