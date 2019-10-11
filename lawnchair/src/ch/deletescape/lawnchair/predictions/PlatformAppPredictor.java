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

import android.app.prediction.AppPredictor;
import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;
import java.util.ArrayList;
import java.util.List;

public class PlatformAppPredictor extends AppPredictorCompat {

    private AppPredictor mPredictor;

    public PlatformAppPredictor(@NonNull Context context,
            @NonNull Client client, int count,
            Bundle extras) {
        super(context, client, count, extras);

        AppPredictionManager apm = context.getSystemService(AppPredictionManager.class);

        mPredictor = apm.createAppPredictionSession(
                new AppPredictionContext.Builder(context)
                        .setUiSurface(client.id)
                        .setPredictedTargetCount(count)
                        .setExtras(extras)
                        .build());
        mPredictor.registerPredictionUpdates(context.getMainExecutor(),
                wrapCallback(PredictionUiStateManager.INSTANCE.get(context).appPredictorCallback(client)));
        mPredictor.requestPredictionUpdate();
    }

    @Override
    public void notifyAppTargetEvent(@NonNull AppTargetEventCompat event) {
        mPredictor.notifyAppTargetEvent(event.toPlatformType());
    }

    @Override
    public void requestPredictionUpdate() {
        mPredictor.requestPredictionUpdate();
    }

    @Override
    public void destroy() {
        mPredictor.destroy();
    }

    private android.app.prediction.AppPredictor.Callback wrapCallback(AppPredictorCompat.Callback callback) {
        return platformTargets -> {
            List<AppTargetCompat> targets = new ArrayList<>();
            for (android.app.prediction.AppTarget platformTarget : platformTargets) {
                targets.add(new AppTargetCompat(platformTarget));
            }
            callback.onTargetsAvailable(targets);
        };
    }
}
