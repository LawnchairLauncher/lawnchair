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
package com.android.launcher3.util;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.EdgeEffect;

import com.android.launcher3.Utilities;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay;

/**
 * Extension of {@link EdgeEffect} which shows the Launcher overlay
 */
public class OverlayEdgeEffect extends EdgeEffectCompat {

    private final LauncherOverlay mOverlay;
    private final boolean mIsRtl;

    private float mDistance;
    private boolean mIsScrolling;

    public OverlayEdgeEffect(Context context, LauncherOverlay overlay) {
        super(context);
        mOverlay = overlay;
        mIsRtl = Utilities.isRtl(context.getResources());

    }

    @Override
    public float getDistance() {
        return mDistance;
    }

    public float onPullDistance(float deltaDistance, float displacement) {
        mDistance = Math.max(0f, deltaDistance + mDistance);
        if (!mIsScrolling) {
            mOverlay.onScrollInteractionBegin();
            mIsScrolling = true;
        }
        mOverlay.onScrollChange(mDistance, mIsRtl);
        return mDistance > 0 ? deltaDistance : 0;
    }

    @Override
    public void onAbsorb(int velocity) { }

    @Override
    public boolean isFinished() {
        return mDistance <= 0;
    }

    @Override
    public void onRelease() {
        if (mIsScrolling) {
            mDistance = 0;
            mOverlay.onScrollInteractionEnd();
            mIsScrolling = false;
        }
    }

    @Override
    public boolean draw(Canvas canvas) {
        return false;
    }

    public void finish() {
        mDistance = 0;
    }
}
