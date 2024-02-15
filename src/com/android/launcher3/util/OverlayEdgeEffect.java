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
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.EdgeEffect;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Utilities;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayTouchProxy;

/**
 * Extension of {@link EdgeEffect} which shows the Launcher overlay
 */
public class OverlayEdgeEffect extends EdgeEffectCompat {

    protected float mDistance;
    protected final LauncherOverlayTouchProxy mOverlay;
    protected boolean mIsScrolling;
    protected final boolean mIsRtl;

    public OverlayEdgeEffect(Context context, LauncherOverlayTouchProxy overlay) {
        super(context);
        mOverlay = overlay;
        mIsRtl = Utilities.isRtl(context.getResources());
    }

    @Override
    public float getDistance() {
        return mDistance;
    }

    public float onPullDistance(float deltaDistance, float displacement) {
        // Fallback implementation, will never actually get called
        if (BuildConfig.IS_DEBUG_DEVICE) {
            throw new RuntimeException("Wrong method called");
        }
        MotionEvent mv = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE, displacement, 0, 0);
        try {
            return onPullDistance(deltaDistance, displacement, mv);
        } finally {
            mv.recycle();
        }
    }

    @Override
    public float onPullDistance(float deltaDistance, float displacement, MotionEvent ev) {
        mDistance = Math.max(0f, deltaDistance + mDistance);
        if (!mIsScrolling) {
            int originalAction = ev.getAction();
            ev.setAction(MotionEvent.ACTION_DOWN);
            mOverlay.onOverlayMotionEvent(ev, 0);
            ev.setAction(originalAction);
            mIsScrolling = true;
        }
        mOverlay.onOverlayMotionEvent(ev, mDistance);
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
        // Fallback implementation, will never actually get called
        if (BuildConfig.IS_DEBUG_DEVICE) {
            throw new RuntimeException("Wrong method called");
        }
        MotionEvent mv = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, mDistance, 0, 0);
        onRelease(mv);
        mv.recycle();
    }

    @Override
    public void onFlingVelocity(int velocity) {
        mOverlay.onFlingVelocity(velocity);
    }

    @Override
    public void onRelease(MotionEvent ev) {
        if (mIsScrolling) {
            int originalAction = ev.getAction();
            ev.setAction(MotionEvent.ACTION_UP);
            mOverlay.onOverlayMotionEvent(ev, mDistance);
            ev.setAction(originalAction);

            mDistance = 0;
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
