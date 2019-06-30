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
import ch.deletescape.lawnchair.loadSmallIcon
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.CardData
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.Line
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.R

@Keep
class NowPlayingProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller) {

    private val context = controller.context
    private val media = MediaListener(context, this::reload)
    private val defaultIcon = context.getDrawable(R.drawable.ic_music_note)!!.toBitmap()!!

    init {
        media.onResume()
    }

    private fun getEventCard(): CardData? {
        if (!media.isTracking ) return null

        val notification = media.playingNotification?.notification ?: return null
        val icon = notification.loadSmallIcon(context)?.toBitmap() ?: defaultIcon

        val lines = mutableListOf<Line>()
        lines.add(Line(media.title.toString()))
        if (!TextUtils.isEmpty(media.artist)) {
            lines.add(Line(media.artist.toString()))
        } else {
            lines.add(Line(getApp(media.`package`).toString()))
        }
        return CardData(icon, lines, notification.contentIntent, true)
    }

    private fun reload() {
        updateData(null, getEventCard())
    }

    override fun onDestroy() {
        super.onDestroy()
        media.onPause()
    }
}
