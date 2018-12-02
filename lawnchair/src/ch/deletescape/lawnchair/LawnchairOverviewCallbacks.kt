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

package ch.deletescape.lawnchair

import android.content.Context
import android.support.annotation.Keep
import com.android.launcher3.LauncherAppState
import com.android.launcher3.config.FeatureFlags
import com.android.quickstep.OverviewCallbacks
import com.google.android.apps.nexuslauncher.PredictionUiStateManager

@Keep
class LawnchairOverviewCallbacks(private val context: Context) : OverviewCallbacks() {

    override fun onInitOverviewTransition() {
        super.onInitOverviewTransition()
        if (FeatureFlags.REFLECTION_FORCE_OVERVIEW_MODE) return
        PredictionUiStateManager.getInstance(context).switchClient(PredictionUiStateManager.Client.OVERVIEW)
    }

    override fun onResetOverview() {
        super.onResetOverview()
        if (FeatureFlags.REFLECTION_FORCE_OVERVIEW_MODE) return
        PredictionUiStateManager.getInstance(context).switchClient(PredictionUiStateManager.Client.HOME)
    }

    override fun closeAllWindows() {
        super.closeAllWindows()
        getActivity()?.let { launcher ->
            launcher.googleNow?.let { client ->
                if (launcher.isStarted && !launcher.isForceInvisible) {
                    client.hideOverlay(150)
                } else {
                    client.hideOverlay(false)
                }
            }
        }
    }

    private fun getActivity(): LawnchairLauncher? {
        return LauncherAppState.getInstanceNoCreate().model.callback as? LawnchairLauncher
    }
}
