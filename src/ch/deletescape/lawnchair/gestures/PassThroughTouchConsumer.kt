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

package ch.deletescape.lawnchair.gestures

import android.annotation.TargetApi
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import com.android.quickstep.MotionEventQueue
import com.android.quickstep.TouchConsumer

@TargetApi(Build.VERSION_CODES.P)
open class PassThroughTouchConsumer(protected val target: TouchConsumer) : TouchConsumer {

    protected open var passThroughEnabled = true

    override fun accept(ev: MotionEvent) {
        if (passThroughEnabled || ev.actionMasked != MotionEvent.ACTION_MOVE) target.accept(ev)
    }

    override fun reset() {
        if (passThroughEnabled) target.reset()
    }

    override fun updateTouchTracking(interactionType: Int) {
        if (passThroughEnabled) target.updateTouchTracking(interactionType)
    }

    override fun onQuickScrubEnd() {
        if (passThroughEnabled) target.onQuickScrubEnd()
    }

    override fun onQuickScrubProgress(progress: Float) {
        if (passThroughEnabled) target.onQuickScrubProgress(progress)
    }

    override fun onQuickStep(ev: MotionEvent?) {
        if (passThroughEnabled) target.onQuickStep(ev)
    }

    override fun onCommand(command: Int) {
        if (passThroughEnabled) target.onCommand(command)
    }

    override fun preProcessMotionEvent(ev: MotionEvent?) {
        if (passThroughEnabled) target.preProcessMotionEvent(ev)
    }

    override fun getIntrimChoreographer(queue: MotionEventQueue?): Choreographer? {
        return if (passThroughEnabled) target.getIntrimChoreographer(queue) else null
    }

    override fun deferInit() {
        if (passThroughEnabled) target.deferInit()
    }

    override fun deferNextEventToMainThread(): Boolean {
        return if (passThroughEnabled) target.deferNextEventToMainThread() else false
    }

    override fun forceToLauncherConsumer(): Boolean {
        return if (passThroughEnabled) target.forceToLauncherConsumer() else false
    }

    override fun onShowOverviewFromAltTab() {
        target.onShowOverviewFromAltTab()
    }
}
