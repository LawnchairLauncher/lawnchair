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

package com.android.launcher3.taskbar;

import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.navbutton.NearestTouchFrame;
import com.android.systemui.shared.statusbar.phone.BarTransitions;

import java.io.PrintWriter;

/** Manages task bar transitions */
public class TaskbarTransitions extends BarTransitions implements
        TaskbarControllers.LoggableTaskbarController {

    private final TaskbarActivityContext mContext;

    private boolean mWallpaperVisible;

    private boolean mLightsOut;
    private boolean mAutoDim;
    private View mNavButtons;
    private float mDarkIntensity;

    private final NearestTouchFrame mView;

    public TaskbarTransitions(TaskbarActivityContext context, NearestTouchFrame view) {
        super(view, R.drawable.nav_background);

        mContext = context;
        mView = view;
    }

    void init() {
        mView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    mNavButtons = mView.findViewById(R.id.end_nav_buttons);
                    applyLightsOut(false, true);
                });
        mNavButtons = mView.findViewById(R.id.end_nav_buttons);

        applyModeBackground(-1, getMode(), false /*animate*/);
        applyLightsOut(false /*animate*/, true /*force*/);
        if (mContext.isPhoneButtonNavMode()) {
            mBarBackground.setOverrideAlpha(1);
        }
    }

    void setWallpaperVisibility(boolean visible) {
        mWallpaperVisible = visible;
        applyLightsOut(true, false);
    }

    @Override
    public void setAutoDim(boolean autoDim) {
        // Ensure we aren't in gestural nav if we are triggering auto dim
        if (autoDim && !mContext.isPhoneButtonNavMode()) {
            return;
        }
        if (mAutoDim == autoDim) return;
        mAutoDim = autoDim;
        applyLightsOut(true, false);
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyLightsOut(animate, false /*force*/);
    }

    private void applyLightsOut(boolean animate, boolean force) {
        // apply to lights out
        applyLightsOut(isLightsOut(getMode()), animate, force);
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut) return;

        mLightsOut = lightsOut;
        if (mNavButtons == null) return;

        // ok, everyone, stop it right there
        mNavButtons.animate().cancel();

        // Bump percentage by 10% if dark.
        float darkBump = mDarkIntensity / 10;
        final float navButtonsAlpha = lightsOut ? 0.6f + darkBump : 1f;

        if (!animate) {
            mNavButtons.setAlpha(navButtonsAlpha);
        } else {
            final int duration = lightsOut ? LIGHTS_OUT_DURATION : LIGHTS_IN_DURATION;
            mNavButtons.animate()
                    .alpha(navButtonsAlpha)
                    .setDuration(duration)
                    .start();
        }
    }

    void onDarkIntensityChanged(float darkIntensity) {
        mDarkIntensity = darkIntensity;
        if (mAutoDim) {
            applyLightsOut(false, true);
        }
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarTransitions:");

        pw.println(prefix + "\tmMode=" + getMode());
        pw.println(prefix + "\tmAlwaysOpaque: " + isAlwaysOpaque());
        pw.println(prefix + "\tmWallpaperVisible: " + mWallpaperVisible);
        pw.println(prefix + "\tmLightsOut: " + mLightsOut);
        pw.println(prefix + "\tmAutoDim: " + mAutoDim);
        pw.println(prefix + "\tbg overrideAlpha: " + mBarBackground.getOverrideAlpha());
        pw.println(prefix + "\tbg color: " + mBarBackground.getColor());
        pw.println(prefix + "\tbg frame: " + mBarBackground.getFrame());
    }
}
