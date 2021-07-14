/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3;

import android.view.KeyEvent;
import android.view.View;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.PredictedAppIcon;
import com.android.launcher3.uioverrides.QuickstepLauncher;

import java.util.List;

public class QuickstepAccessibilityDelegate extends LauncherAccessibilityDelegate {

    public QuickstepAccessibilityDelegate(QuickstepLauncher launcher) {
        super(launcher);
        mActions.put(PIN_PREDICTION, new LauncherAction(
                PIN_PREDICTION, R.string.pin_prediction, KeyEvent.KEYCODE_P));
    }

    @Override
    protected void getSupportedActions(View host, ItemInfo item, List<LauncherAction> out) {
        if (host instanceof PredictedAppIcon && !((PredictedAppIcon) host).isPinned()) {
            out.add(new LauncherAction(PIN_PREDICTION, R.string.pin_prediction,
                    KeyEvent.KEYCODE_P));
        }
        super.getSupportedActions(host, item, out);
    }

    @Override
    protected boolean performAction(View host, ItemInfo item, int action, boolean fromKeyboard) {
        QuickstepLauncher launcher = (QuickstepLauncher) mLauncher;
        if (action == PIN_PREDICTION) {
            if (launcher.getHotseatPredictionController() == null) {
                return false;
            }
            launcher.getHotseatPredictionController().pinPrediction(item);
            return true;
        }
        return super.performAction(host, item, action, fromKeyboard);
    }
}
