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

package com.android.wm.shell.pip.tv;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.util.Size;
import android.view.Gravity;
import android.view.View;

import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.SizeSpecSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * TV specific values of the current state of the PiP bounds.
 */
public class TvPipBoundsState extends PipBoundsState {

    public static final int ORIENTATION_UNDETERMINED = 0;
    public static final int ORIENTATION_VERTICAL = 1;
    public static final int ORIENTATION_HORIZONTAL = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"ORIENTATION_"}, value = {
            ORIENTATION_UNDETERMINED,
            ORIENTATION_VERTICAL,
            ORIENTATION_HORIZONTAL
    })
    public @interface Orientation {
    }

    private final Context mContext;

    private int mDefaultGravity;
    private int mTvPipGravity;
    private int mPreviousCollapsedGravity;
    private boolean mIsRtl;

    private final boolean mIsTvExpandedPipSupported;
    private boolean mIsTvPipExpanded;
    private boolean mTvPipManuallyCollapsed;
    private float mDesiredTvExpandedAspectRatio;
    @Orientation
    private int mTvFixedPipOrientation;
    @Nullable
    private Size mTvExpandedSize;
    @NonNull
    private Insets mPipMenuPermanentDecorInsets = Insets.NONE;
    @NonNull
    private Insets mPipMenuTemporaryDecorInsets = Insets.NONE;

    public TvPipBoundsState(@NonNull Context context,
            @NonNull SizeSpecSource sizeSpecSource,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState) {
        super(context, sizeSpecSource, pipDisplayLayoutState);
        mContext = context;
        updateDefaultGravity();
        mTvPipGravity = mDefaultGravity;
        mPreviousCollapsedGravity = mDefaultGravity;
        mIsTvExpandedPipSupported = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE);
    }

    public int getDefaultGravity() {
        return mDefaultGravity;
    }

    private void updateDefaultGravity() {
        boolean isRtl = mContext.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
        mDefaultGravity = Gravity.BOTTOM | (isRtl ? Gravity.LEFT : Gravity.RIGHT);

        if (mIsRtl != isRtl) {
            int prevGravityX = mPreviousCollapsedGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            int prevGravityY = mPreviousCollapsedGravity & Gravity.VERTICAL_GRAVITY_MASK;
            if ((prevGravityX & Gravity.RIGHT) == Gravity.RIGHT) {
                mPreviousCollapsedGravity = Gravity.LEFT | prevGravityY;
            } else if ((prevGravityX & Gravity.LEFT) == Gravity.LEFT) {
                mPreviousCollapsedGravity = Gravity.RIGHT | prevGravityY;
            }
        }
        mIsRtl = isRtl;
    }

    @Override
    public void onConfigurationChanged() {
        super.onConfigurationChanged();
        updateDefaultGravity();
    }

    /**
     * Initialize states when first entering PiP.
     */
    @Override
    public void setBoundsStateForEntry(ComponentName componentName, ActivityInfo activityInfo,
            PictureInPictureParams params, PipBoundsAlgorithm pipBoundsAlgorithm) {
        super.setBoundsStateForEntry(componentName, activityInfo, params, pipBoundsAlgorithm);
        if (params == null) {
            return;
        }
        setDesiredTvExpandedAspectRatio(params.getExpandedAspectRatioFloat(), true);
    }

    /** Resets the TV PiP state for a new activity. */
    public void resetTvPipState() {
        mTvFixedPipOrientation = ORIENTATION_UNDETERMINED;
        mTvPipGravity = mDefaultGravity;
        mPreviousCollapsedGravity = mDefaultGravity;
        mIsTvPipExpanded = false;
        mTvPipManuallyCollapsed = false;
    }

    /** Set the tv expanded bounds of PiP */
    public void setTvExpandedSize(@Nullable Size size) {
        mTvExpandedSize = size;
    }

    /** Get the expanded size of the PiP. */
    @Nullable
    public Size getTvExpandedSize() {
        return mTvExpandedSize;
    }

    /** Set the PiP aspect ratio for the expanded PiP (TV) that is desired by the app. */
    public void setDesiredTvExpandedAspectRatio(float expandedAspectRatio, boolean override) {
        if (override || mTvFixedPipOrientation == ORIENTATION_UNDETERMINED) {
            resetTvPipState();
            mDesiredTvExpandedAspectRatio = expandedAspectRatio;
            if (expandedAspectRatio != 0) {
                if (expandedAspectRatio > 1) {
                    mTvFixedPipOrientation = ORIENTATION_HORIZONTAL;
                } else {
                    mTvFixedPipOrientation = ORIENTATION_VERTICAL;
                }
            }
            return;
        }
        if ((expandedAspectRatio > 1 && mTvFixedPipOrientation == ORIENTATION_HORIZONTAL)
                || (expandedAspectRatio <= 1 && mTvFixedPipOrientation == ORIENTATION_VERTICAL)
                || expandedAspectRatio == 0) {
            mDesiredTvExpandedAspectRatio = expandedAspectRatio;
        }
    }

    /**
     * Get the aspect ratio for the expanded PiP (TV) that is desired, or {@code 0} if it is not
     * enabled by the app.
     */
    public float getDesiredTvExpandedAspectRatio() {
        return mDesiredTvExpandedAspectRatio;
    }

    /** Returns the fixed orientation of the expanded PiP on TV. */
    @Orientation
    public int getTvFixedPipOrientation() {
        return mTvFixedPipOrientation;
    }

    /** Sets the current gravity of the TV PiP. */
    public void setTvPipGravity(int gravity) {
        mTvPipGravity = gravity;
    }

    /** Returns the current gravity of the TV PiP. */
    public int getTvPipGravity() {
        return mTvPipGravity;
    }

    public void setTvPipPreviousCollapsedGravity(int gravity) {
        mPreviousCollapsedGravity = gravity;
    }

    public int getTvPipPreviousCollapsedGravity() {
        return mPreviousCollapsedGravity;
    }

    /** Sets whether the TV PiP is currently expanded. */
    public void setTvPipExpanded(boolean expanded) {
        mIsTvPipExpanded = expanded;
    }

    /** Returns whether the TV PiP is currently expanded. */
    public boolean isTvPipExpanded() {
        return mIsTvPipExpanded;
    }

    /** Sets whether the user has manually collapsed the TV PiP. */
    public void setTvPipManuallyCollapsed(boolean collapsed) {
        mTvPipManuallyCollapsed = collapsed;
    }

    /** Returns whether the user has manually collapsed the TV PiP. */
    public boolean isTvPipManuallyCollapsed() {
        return mTvPipManuallyCollapsed;
    }

    /** Returns whether expanded PiP is supported by the device. */
    public boolean isTvExpandedPipSupported() {
        return mIsTvExpandedPipSupported;
    }

    public void setPipMenuPermanentDecorInsets(@NonNull Insets permanentInsets) {
        mPipMenuPermanentDecorInsets = permanentInsets;
    }

    @NonNull
    public Insets getPipMenuPermanentDecorInsets() {
        return mPipMenuPermanentDecorInsets;
    }

    public void setPipMenuTemporaryDecorInsets(@NonNull Insets temporaryDecorInsets) {
        mPipMenuTemporaryDecorInsets = temporaryDecorInsets;
    }

    @NonNull
    public Insets getPipMenuTemporaryDecorInsets() {
        return mPipMenuTemporaryDecorInsets;
    }
}
