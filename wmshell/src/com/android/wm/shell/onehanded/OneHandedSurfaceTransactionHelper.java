/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.onehanded;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import com.android.wm.shell.R;

import java.io.PrintWriter;

/**
 * Abstracts the common operations on {@link SurfaceControl.Transaction} for OneHanded transition.
 */
public class OneHandedSurfaceTransactionHelper {
    private static final String TAG = "OneHandedSurfaceTransactionHelper";

    private final boolean mEnableCornerRadius;
    private final float mCornerRadius;
    private final float mCornerRadiusAdjustment;

    public OneHandedSurfaceTransactionHelper(Context context) {
        final Resources res = context.getResources();
        mCornerRadiusAdjustment = res.getDimension(
                com.android.internal.R.dimen.rounded_corner_radius_adjustment);
        mCornerRadius = res.getDimension(com.android.internal.R.dimen.rounded_corner_radius)
                - mCornerRadiusAdjustment;
        mEnableCornerRadius = res.getBoolean(R.bool.config_one_handed_enable_round_corner);
    }

    /**
     * Operates the translation (setPosition) on a given transaction and leash
     *
     * @return same {@link OneHandedSurfaceTransactionHelper} instance for method chaining
     */
    OneHandedSurfaceTransactionHelper translate(SurfaceControl.Transaction tx, SurfaceControl leash,
            float offset) {
        tx.setPosition(leash, 0, offset);
        return this;
    }

    /**
     * Operates the crop (setMatrix) on a given transaction and leash
     *
     * @return same {@link OneHandedSurfaceTransactionHelper} instance for method chaining
     */
    OneHandedSurfaceTransactionHelper crop(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect destinationBounds) {
        tx.setWindowCrop(leash, destinationBounds.width(), destinationBounds.height());
        return this;
    }

    /**
     * Operates the round corner radius on a given transaction and leash
     *
     * @return same {@link OneHandedSurfaceTransactionHelper} instance for method chaining
     */
    OneHandedSurfaceTransactionHelper round(SurfaceControl.Transaction tx, SurfaceControl leash) {
        if (mEnableCornerRadius) {
            tx.setCornerRadius(leash, mCornerRadius);
        }
        return this;
    }

    interface SurfaceControlTransactionFactory {
        SurfaceControl.Transaction getTransaction();
    }

    void dump(@NonNull PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG + "states: ");
        pw.print(innerPrefix + "mEnableCornerRadius=");
        pw.println(mEnableCornerRadius);
        pw.print(innerPrefix + "mCornerRadiusAdjustment=");
        pw.println(mCornerRadiusAdjustment);
        pw.print(innerPrefix + "mCornerRadius=");
        pw.println(mCornerRadius);
    }
}
