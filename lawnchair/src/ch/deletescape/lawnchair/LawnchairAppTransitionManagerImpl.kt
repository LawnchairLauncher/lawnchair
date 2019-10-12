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

package ch.deletescape.lawnchair

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Pair
import androidx.annotation.Keep
import ch.deletescape.lawnchair.util.InvertedMultiValueAlpha
import ch.deletescape.lawnchair.views.LawnchairBackgroundView
import com.android.launcher3.LauncherAppTransitionManagerImpl
import com.android.launcher3.LauncherState.ALL_APPS
import com.android.launcher3.LauncherState.OVERVIEW
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.anim.Interpolators.LINEAR

@Keep
class LawnchairAppTransitionManagerImpl(context: Context) :
        LauncherAppTransitionManagerImpl(context) {

    private val launcher = LawnchairLauncher.getLauncher(context)
    private val background = launcher.background

    override fun getLauncherContentAnimator(isAppOpening: Boolean,
                                            trans: FloatArray?): Pair<AnimatorSet, Runnable> {
        val results = super.getLauncherContentAnimator(isAppOpening, trans)
        val blurAlphas = if (isAppOpening) floatArrayOf(0f, 1f) else floatArrayOf(1f, 0f)
        if (!launcher.isInState(ALL_APPS) && !launcher.isInState(OVERVIEW)) {
            background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS).value = blurAlphas[0]
            val blurAlpha = ObjectAnimator.ofFloat(
                    background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS),
                    InvertedMultiValueAlpha.VALUE, *blurAlphas)
            blurAlpha.duration = 217
            blurAlpha.interpolator = LINEAR
            results.first.play(blurAlpha)
        }
        return results
    }

    override fun createLauncherResumeAnimation(anim: AnimatorSet) {
        super.createLauncherResumeAnimation(anim)

        background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS).value = 1f
        val blurAnim = ObjectAnimator.ofFloat(
                background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS),
                InvertedMultiValueAlpha.VALUE, 1f, 0f)
        blurAnim.startDelay = LAUNCHER_RESUME_START_DELAY.toLong()
        blurAnim.duration = 333
        blurAnim.interpolator = Interpolators.DEACCEL_1_7
        anim.play(blurAnim)
    }

    override fun resetContentView() {
        super.resetContentView()
        background.blurAlphas.getProperty(LawnchairBackgroundView.ALPHA_INDEX_TRANSITIONS).value = 0f
    }
}
