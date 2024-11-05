/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.back;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import android.annotation.NonNull;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.SurfaceControl;

import com.android.internal.graphics.ColorUtils;
import com.android.internal.view.AppearanceRegion;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

/**
 * Controls background surface for the back animations
 */
public class BackAnimationBackground {
    private static final int BACKGROUND_LAYER = -1;

    private static final int NO_APPEARANCE = 0;

    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private SurfaceControl mBackgroundSurface;

    private StatusBarCustomizer mCustomizer;
    private boolean mIsRequestingStatusBarAppearance;
    private boolean mBackgroundIsDark;
    private Rect mStartBounds;
    private int mStatusbarHeight;

    public BackAnimationBackground(RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer) {
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
    }

    /**
     * Ensures the back animation background color layer is present.
     *
     * @param startRect The start bounds of the closing target.
     * @param color The background color.
     * @param transaction The animation transaction.
     * @param statusbarHeight The height of the statusbar (in px).
     */
    public void ensureBackground(Rect startRect, int color,
            @NonNull SurfaceControl.Transaction transaction, int statusbarHeight) {
        if (mBackgroundSurface != null) {
            return;
        }

        mBackgroundIsDark = ColorUtils.calculateLuminance(color) < 0.5f;

        final float[] colorComponents = new float[] { Color.red(color) / 255.f,
                Color.green(color) / 255.f, Color.blue(color) / 255.f };

        final SurfaceControl.Builder colorLayerBuilder = new SurfaceControl.Builder()
                .setName("back-animation-background")
                .setCallsite("BackAnimationBackground")
                .setColorLayer();

        mRootTaskDisplayAreaOrganizer.attachToDisplayArea(DEFAULT_DISPLAY, colorLayerBuilder);
        mBackgroundSurface = colorLayerBuilder.build();
        transaction.setColor(mBackgroundSurface, colorComponents)
                .setLayer(mBackgroundSurface, BACKGROUND_LAYER)
                .show(mBackgroundSurface);
        mStartBounds = startRect;
        mIsRequestingStatusBarAppearance = false;
        mStatusbarHeight = statusbarHeight;
    }

    /**
     * Remove the back animation background.
     *
     * @param transaction The animation transaction.
     */
    public void removeBackground(@NonNull SurfaceControl.Transaction transaction) {
        if (mBackgroundSurface == null) {
            return;
        }

        if (mBackgroundSurface.isValid()) {
            transaction.remove(mBackgroundSurface);
        }
        mBackgroundSurface = null;
        mIsRequestingStatusBarAppearance = false;
    }

    /**
     * Attach a {@link StatusBarCustomizer} instance to allow status bar animate with back progress.
     *
     * @param customizer The {@link StatusBarCustomizer} to be used.
     */
    void setStatusBarCustomizer(StatusBarCustomizer customizer) {
        mCustomizer = customizer;
    }

    /**
     * Update back animation background with for the progress.
     *
     * @param top The top coordinate of the closing target
     */
    public void customizeStatusBarAppearance(int top) {
        if (mCustomizer == null || mStartBounds.isEmpty()) {
            return;
        }

        final boolean shouldCustomizeSystemBar = top > mStatusbarHeight / 2;
        if (shouldCustomizeSystemBar == mIsRequestingStatusBarAppearance) {
            return;
        }

        mIsRequestingStatusBarAppearance = shouldCustomizeSystemBar;
        if (mIsRequestingStatusBarAppearance) {
            final AppearanceRegion region = new AppearanceRegion(!mBackgroundIsDark
                    ? APPEARANCE_LIGHT_STATUS_BARS : NO_APPEARANCE,
                    mStartBounds);
            mCustomizer.customizeStatusBarAppearance(region);
        } else {
            resetStatusBarCustomization();
        }
    }

    /**
     * Resets the statusbar customization
     */
    public void resetStatusBarCustomization() {
        mCustomizer.customizeStatusBarAppearance(null);
    }
}
