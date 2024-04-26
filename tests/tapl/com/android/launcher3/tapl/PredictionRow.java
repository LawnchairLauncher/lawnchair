/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.tapl;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

/** View containing prediction app icons */
public class PredictionRow {

    private static final String PREDICTION_ROW_ID = "prediction_row";
    private static final String PREDICTION_APP_ID = "icon";
    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mAllAppsHeader;
    private final UiObject2 mPredictionRow;

    PredictionRow(LauncherInstrumentation launcherInstrumentation,
            UiObject2 allAppsHeader) {
        mLauncher = launcherInstrumentation;
        mAllAppsHeader = allAppsHeader;
        mPredictionRow = mLauncher.waitForObjectInContainer(mAllAppsHeader,
                PREDICTION_ROW_ID);
        verifyAppsPresentInsidePredictionRow();
        verifyPredictionRowAppsCount();
    }

    /** Verify that one app is present in prediction row view. */
    private void verifyAppsPresentInsidePredictionRow() {
        mLauncher.waitForObjectInContainer(mPredictionRow,
                PREDICTION_APP_ID);
    }

    /** Verify that prediction row apps count is same as launcher apps column count. */
    private void verifyPredictionRowAppsCount() {
        mLauncher.assertEquals("PredictionRow app count mismatch", mLauncher.getNumAllAppsColumns(),
                getPredictionRowAppsCount());
    }

    /**
     * Returns an app icon found in the prediction row. This fails if any icon is not
     * found.
     */
    @NonNull
    private HomeAppIcon getAnyAppIcon() {
        return new AllAppsAppIcon(mLauncher,
                mPredictionRow.findObject(AppIcon.getAnyAppIconSelector()));
    }

    /**
     * Returns the size of prediction row apps count.
     */
    private int getPredictionRowAppsCount() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get all prediction row icons")) {
            return mPredictionRow.findObjects(AppIcon.getAnyAppIconSelector()).size();
        }
    }
}
