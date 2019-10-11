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

package ch.deletescape.lawnchair.predictions;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;
import java.util.List;

public abstract class AppPredictorCompat {

    public AppPredictorCompat(@NonNull Context context, @NonNull Client client, int count, Bundle extras) {

    }

    public abstract void notifyAppTargetEvent(@NonNull AppTargetEventCompat event);

    public abstract void requestPredictionUpdate();

    public abstract void destroy();

    /**
     * Callback for receiving prediction updates.
     */
    public interface Callback {

        /**
         * Called when a new set of predicted app targets are available.
         * @param targets Sorted list of predicted targets.
         */
        void onTargetsAvailable(@NonNull List<AppTargetCompat> targets);
    }
}
