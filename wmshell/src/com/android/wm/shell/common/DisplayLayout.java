/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.common;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.UI_MODE_TYPE_CAR;
import static android.content.res.Configuration.UI_MODE_TYPE_MASK;
import static android.os.Process.SYSTEM_UID;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS;
import static android.view.Display.FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.InsetsState;
import android.view.Surface;
import android.view.WindowInsets;

import androidx.annotation.VisibleForTesting;

import com.android.internal.R;
import com.android.internal.policy.SystemBarUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Contains information about the layout-properties of a display. This refers to internal layout
 * like insets/cutout/rotation. In general, this can be thought of as the shell analog to
 * DisplayPolicy.
 */
public class DisplayLayout {
    @IntDef(prefix = { "NAV_BAR_" }, value = {
            NAV_BAR_LEFT,
            NAV_BAR_RIGHT,
            NAV_BAR_BOTTOM,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NavBarPosition {}

    // Navigation bar position values
    public static final int NAV_BAR_LEFT = 1 << 0;
    public static final int NAV_BAR_RIGHT = 1 << 1;
    public static final int NAV_BAR_BOTTOM = 1 << 2;

    private int mUiMode;
    private int mWidth;
    private int mHeight;
    private DisplayCutout mCutout;
    private int mRotation;
    private int mDensityDpi;
    private final Rect mNonDecorInsets = new Rect();
    private final Rect mStableInsets = new Rect();
    private boolean mHasNavigationBar = false;
    private boolean mHasStatusBar = false;
    private int mNavBarFrameHeight = 0;
    private boolean mAllowSeamlessRotationDespiteNavBarMoving = false;
    private boolean mNavigationBarCanMove = false;
    private boolean mReverseDefaultRotation = false;
    private InsetsState mInsetsState = new InsetsState();

    /**
     * Different from {@link #equals(Object)}, this method compares the basic geometry properties
     * of two {@link DisplayLayout} objects including width, height, rotation, density, cutout.
     * @return {@code true} if the given {@link DisplayLayout} is identical geometry wise.
     */
    public boolean isSameGeometry(@NonNull DisplayLayout other) {
        return mWidth == other.mWidth
                && mHeight == other.mHeight
                && mRotation == other.mRotation
                && mDensityDpi == other.mDensityDpi
                && Objects.equals(mCutout, other.mCutout);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisplayLayout)) return false;
        final DisplayLayout other = (DisplayLayout) o;
        return mUiMode == other.mUiMode
                && mWidth == other.mWidth
                && mHeight == other.mHeight
                && Objects.equals(mCutout, other.mCutout)
                && mRotation == other.mRotation
                && mDensityDpi == other.mDensityDpi
                && Objects.equals(mNonDecorInsets, other.mNonDecorInsets)
                && Objects.equals(mStableInsets, other.mStableInsets)
                && mHasNavigationBar == other.mHasNavigationBar
                && mHasStatusBar == other.mHasStatusBar
                && mAllowSeamlessRotationDespiteNavBarMoving
                        == other.mAllowSeamlessRotationDespiteNavBarMoving
                && mNavigationBarCanMove == other.mNavigationBarCanMove
                && mReverseDefaultRotation == other.mReverseDefaultRotation
                && mNavBarFrameHeight == other.mNavBarFrameHeight
                && Objects.equals(mInsetsState, other.mInsetsState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUiMode, mWidth, mHeight, mCutout, mRotation, mDensityDpi,
                mNonDecorInsets, mStableInsets, mHasNavigationBar, mHasStatusBar,
                mNavBarFrameHeight, mAllowSeamlessRotationDespiteNavBarMoving,
                mNavigationBarCanMove, mReverseDefaultRotation, mInsetsState);
    }

    /**
     * Create empty layout.
     */
    public DisplayLayout() {
    }

    /**
     * Construct a custom display layout using a DisplayInfo.
     * @param info
     * @param res
     */
    public DisplayLayout(DisplayInfo info, Resources res, boolean hasNavigationBar,
            boolean hasStatusBar) {
        init(info, res, hasNavigationBar, hasStatusBar);
    }

    /**
     * Construct a display layout based on a live display.
     * @param context Used for resources.
     */
    public DisplayLayout(@NonNull Context context, @NonNull Display rawDisplay) {
        final int displayId = rawDisplay.getDisplayId();
        DisplayInfo info = new DisplayInfo();
        rawDisplay.getDisplayInfo(info);
        init(info, context.getResources(), hasNavigationBar(info, context, displayId),
                hasStatusBar(displayId));
    }

    public DisplayLayout(DisplayLayout dl) {
        set(dl);
    }

    /** sets this DisplayLayout to a copy of another on. */
    public void set(DisplayLayout dl) {
        mUiMode = dl.mUiMode;
        mWidth = dl.mWidth;
        mHeight = dl.mHeight;
        mCutout = dl.mCutout;
        mRotation = dl.mRotation;
        mDensityDpi = dl.mDensityDpi;
        mHasNavigationBar = dl.mHasNavigationBar;
        mHasStatusBar = dl.mHasStatusBar;
        mAllowSeamlessRotationDespiteNavBarMoving = dl.mAllowSeamlessRotationDespiteNavBarMoving;
        mNavigationBarCanMove = dl.mNavigationBarCanMove;
        mReverseDefaultRotation = dl.mReverseDefaultRotation;
        mNavBarFrameHeight = dl.mNavBarFrameHeight;
        mNonDecorInsets.set(dl.mNonDecorInsets);
        mStableInsets.set(dl.mStableInsets);
        mInsetsState.set(dl.mInsetsState, true /* copySources */);
    }

    private void init(DisplayInfo info, Resources res, boolean hasNavigationBar,
            boolean hasStatusBar) {
        mUiMode = res.getConfiguration().uiMode;
        mWidth = info.logicalWidth;
        mHeight = info.logicalHeight;
        mRotation = info.rotation;
        mCutout = info.displayCutout;
        mDensityDpi = info.logicalDensityDpi;
        mHasNavigationBar = hasNavigationBar;
        mHasStatusBar = hasStatusBar;
        mAllowSeamlessRotationDespiteNavBarMoving = res.getBoolean(
            R.bool.config_allowSeamlessRotationDespiteNavBarMoving);
        mNavigationBarCanMove = res.getBoolean(R.bool.config_navBarCanMove);
        mReverseDefaultRotation = res.getBoolean(R.bool.config_reverseDefaultRotation);
        recalcInsets(res);
    }

    /**
     * Updates the current insets.
     */
    public void setInsets(Resources res, InsetsState state) {
        mInsetsState = state;
        recalcInsets(res);
    }

    @VisibleForTesting
    void recalcInsets(Resources res) {
        computeNonDecorInsets(res, mRotation, mWidth, mHeight, mCutout, mInsetsState, mUiMode,
                mNonDecorInsets, mHasNavigationBar);
        mStableInsets.set(mNonDecorInsets);
        if (mHasStatusBar) {
            convertNonDecorInsetsToStableInsets(res, mStableInsets, mCutout, mHasStatusBar);
        }
        mNavBarFrameHeight = getNavigationBarFrameHeight(res, mWidth > mHeight);
    }

    /**
     * Apply a rotation to this layout and its parameters.
     */
    public void rotateTo(Resources res, @Surface.Rotation int toRotation) {
        final int origWidth = mWidth;
        final int origHeight = mHeight;
        final int fromRotation = mRotation;
        final int rotationDelta = (toRotation - fromRotation + 4) % 4;
        final boolean changeOrient = (rotationDelta % 2) != 0;

        mRotation = toRotation;
        if (changeOrient) {
            mWidth = origHeight;
            mHeight = origWidth;
        }

        if (mCutout != null) {
            mCutout = mCutout.getRotated(origWidth, origHeight, fromRotation, toRotation);
        }

        recalcInsets(res);
    }

    /** Get this layout's non-decor insets. */
    public Rect nonDecorInsets() {
        return mNonDecorInsets;
    }

    /** Get this layout's stable insets. */
    public Rect stableInsets() {
        return mStableInsets;
    }

    /** Get this layout's width. */
    public int width() {
        return mWidth;
    }

    /** Get this layout's height. */
    public int height() {
        return mHeight;
    }

    /** Get this layout's display rotation. */
    public int rotation() {
        return mRotation;
    }

    /** Get this layout's display density. */
    public int densityDpi() {
        return mDensityDpi;
    }

    /** Get the density scale for the display. */
    public float density() {
        return mDensityDpi * DisplayMetrics.DENSITY_DEFAULT_SCALE;
    }

    /** Get whether this layout is landscape. */
    public boolean isLandscape() {
        return mWidth > mHeight;
    }

    /** Get the navbar frame (or window) height (used by ime). */
    public int navBarFrameHeight() {
        return mNavBarFrameHeight;
    }

    /** @return whether we can seamlessly rotate even if nav-bar can change sides. */
    public boolean allowSeamlessRotationDespiteNavBarMoving() {
        return mAllowSeamlessRotationDespiteNavBarMoving;
    }

    /**
     * Returns {@code true} if the navigation bar will change sides during rotation and the display
     * is not square.
     */
    public boolean navigationBarCanMove() {
        return mNavigationBarCanMove && mWidth != mHeight;
    }

    /** @return the rotation that would make the physical display "upside down". */
    public int getUpsideDownRotation() {
        boolean displayHardwareIsLandscape = mWidth > mHeight;
        if ((mRotation % 2) != 0) {
            displayHardwareIsLandscape = !displayHardwareIsLandscape;
        }
        if (displayHardwareIsLandscape) {
            return mReverseDefaultRotation ? Surface.ROTATION_270 : Surface.ROTATION_90;
        }
        return Surface.ROTATION_180;
    }

    /** Gets the orientation of this layout */
    public int getOrientation() {
        return (mWidth > mHeight) ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT;
    }

    /** Gets the calculated stable-bounds for this layout */
    public void getStableBounds(Rect outBounds) {
        outBounds.set(0, 0, mWidth, mHeight);
        outBounds.inset(mStableInsets);
    }

    /**
     * Gets navigation bar position for this layout
     * @return Navigation bar position for this layout.
     */
    public @NavBarPosition int getNavigationBarPosition(Resources res) {
        return navigationBarPosition(res, mWidth, mHeight, mRotation);
    }

    /** @return {@link DisplayCutout} instance. */
    @Nullable
    public DisplayCutout getDisplayCutout() {
        return mCutout;
    }

    /**
     * Calculates the stable insets if we already have the non-decor insets.
     */
    private void convertNonDecorInsetsToStableInsets(Resources res, Rect inOutInsets,
            DisplayCutout cutout, boolean hasStatusBar) {
        if (!hasStatusBar) {
            return;
        }
        int statusBarHeight = SystemBarUtils.getStatusBarHeight(res, cutout);
        inOutInsets.top = Math.max(inOutInsets.top, statusBarHeight);
    }

    /**
     * Calculates the insets for the areas that could never be removed in Honeycomb, i.e. system
     * bar or button bar.
     *
     * @param displayRotation the current display rotation
     * @param displayWidth the current display width
     * @param displayHeight the current display height
     * @param displayCutout the current display cutout
     * @param outInsets the insets to return
     */
    static void computeNonDecorInsets(Resources res, int displayRotation, int displayWidth,
            int displayHeight, DisplayCutout displayCutout, InsetsState insetsState, int uiMode,
            Rect outInsets, boolean hasNavigationBar) {
        outInsets.setEmpty();

        // Only navigation bar
        if (hasNavigationBar) {
            final Insets insets = insetsState.calculateInsets(
                    insetsState.getDisplayFrame(),
                    WindowInsets.Type.navigationBars(),
                    false /* ignoreVisibility */);
            int position = navigationBarPosition(res, displayWidth, displayHeight, displayRotation);
            int navBarSize =
                    getNavigationBarSize(res, position, displayWidth > displayHeight, uiMode);
            if (position == NAV_BAR_BOTTOM) {
                outInsets.bottom = Math.max(insets.bottom , navBarSize);
            } else if (position == NAV_BAR_RIGHT) {
                outInsets.right = Math.max(insets.right , navBarSize);
            } else if (position == NAV_BAR_LEFT) {
                outInsets.left = Math.max(insets.left , navBarSize);
            }
        }

        if (displayCutout != null) {
            outInsets.left += displayCutout.getSafeInsetLeft();
            outInsets.top += displayCutout.getSafeInsetTop();
            outInsets.right += displayCutout.getSafeInsetRight();
            outInsets.bottom += displayCutout.getSafeInsetBottom();
        }
    }

    static boolean hasNavigationBar(DisplayInfo info, Context context, int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            // Allow a system property to override this. Used by the emulator.
            final String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                return false;
            } else if ("0".equals(navBarOverride)) {
                return true;
            }
            return context.getResources().getBoolean(R.bool.config_showNavigationBar);
        } else {
            boolean isUntrustedVirtualDisplay = info.type == Display.TYPE_VIRTUAL
                    && info.ownerUid != SYSTEM_UID;
            final ContentResolver resolver = context.getContentResolver();
            boolean forceDesktopOnExternal = Settings.Global.getInt(resolver,
                    DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 0) != 0;

            return ((info.flags & FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS) != 0
                    || (forceDesktopOnExternal && !isUntrustedVirtualDisplay));
            // TODO(b/142569966): make sure VR2D and DisplayWindowSettings are moved here somehow.
        }
    }

    static boolean hasStatusBar(int displayId) {
        return displayId == Display.DEFAULT_DISPLAY;
    }

    /** Retrieve navigation bar position from resources based on rotation and size. */
    public static @NavBarPosition int navigationBarPosition(Resources res, int displayWidth,
            int displayHeight, int rotation) {
        boolean navBarCanMove = displayWidth != displayHeight && res.getBoolean(
                com.android.internal.R.bool.config_navBarCanMove);
        if (navBarCanMove && displayWidth > displayHeight) {
            if (rotation == Surface.ROTATION_90) {
                return NAV_BAR_RIGHT;
            } else {
                return NAV_BAR_LEFT;
            }
        }
        return NAV_BAR_BOTTOM;
    }

    /** Retrieve navigation bar size from resources based on side/orientation/ui-mode */
    public static int getNavigationBarSize(Resources res, int navBarSide, boolean landscape,
            int uiMode) {
        final boolean carMode = (uiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_CAR;
        if (carMode) {
            if (navBarSide == NAV_BAR_BOTTOM) {
                return res.getDimensionPixelSize(landscape
                        ? R.dimen.navigation_bar_height_landscape_car_mode
                        : R.dimen.navigation_bar_height_car_mode);
            } else {
                return res.getDimensionPixelSize(R.dimen.navigation_bar_width_car_mode);
            }

        } else {
            if (navBarSide == NAV_BAR_BOTTOM) {
                return res.getDimensionPixelSize(landscape
                        ? R.dimen.navigation_bar_height_landscape
                        : R.dimen.navigation_bar_height);
            } else {
                return res.getDimensionPixelSize(R.dimen.navigation_bar_width);
            }
        }
    }

    /** @see com.android.server.wm.DisplayPolicy#getNavigationBarFrameHeight */
    public static int getNavigationBarFrameHeight(Resources res, boolean landscape) {
        return res.getDimensionPixelSize(landscape
                ? R.dimen.navigation_bar_frame_height_landscape
                : R.dimen.navigation_bar_frame_height);
    }
}
