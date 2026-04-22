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

import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Handler;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.pip.tv.TvPipKeepClearAlgorithm.Placement;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Controller managing the PiP's position.
 * Manages debouncing of PiP movements and scheduling of unstashing.
 */
public class TvPipBoundsController {
    private static final String TAG = "TvPipBoundsController";

    /**
     * Time the calculated PiP position needs to be stable before PiP is moved there,
     * to avoid erratic movement.
     * Some changes will cause the PiP to be repositioned immediately, such as changes to
     * unrestricted keep clear areas.
     */
    @VisibleForTesting
    static final long POSITION_DEBOUNCE_TIMEOUT_MILLIS = 300L;

    private final Context mContext;
    private final Supplier<Long> mClock;
    private final Handler mMainHandler;
    private final TvPipBoundsState mTvPipBoundsState;
    private final TvPipBoundsAlgorithm mTvPipBoundsAlgorithm;

    @Nullable
    private PipBoundsListener mListener;

    private int mResizeAnimationDuration;
    private int mStashDurationMs;
    private Rect mCurrentPlacementBounds;
    private Rect mPipTargetBounds;

    private final Runnable mApplyPendingPlacementRunnable = this::applyPendingPlacement;
    private boolean mPendingStash;
    private Placement mPendingPlacement;
    private int mPendingPlacementAnimationDuration;
    private Runnable mUnstashRunnable;

    public TvPipBoundsController(
            Context context,
            Supplier<Long> clock,
            Handler mainHandler,
            TvPipBoundsState tvPipBoundsState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm) {
        mContext = context;
        mClock = clock;
        mMainHandler = mainHandler;
        mTvPipBoundsState = tvPipBoundsState;
        mTvPipBoundsAlgorithm = tvPipBoundsAlgorithm;

        loadConfigurations();
    }

    private void loadConfigurations() {
        final Resources res = mContext.getResources();
        mResizeAnimationDuration = res.getInteger(R.integer.config_pipResizeAnimationDuration);
        mStashDurationMs = res.getInteger(R.integer.config_pipStashDuration);
    }

    void setListener(PipBoundsListener listener) {
        mListener = listener;
    }

    /**
     * Update the PiP bounds based on the state of the PiP, decors, and keep clear areas.
     * Unless {@code immediate} is {@code true}, the PiP does not move immediately to avoid
     * keep clear areas, but waits for a new position to stay uncontested for
     * {@link #POSITION_DEBOUNCE_TIMEOUT_MILLIS} before moving to it.
     * Temporary decor changes are applied immediately.
     *
     * @param stayAtAnchorPosition If true, PiP will be placed at the anchor position
     * @param disallowStashing     If true, PiP will not be placed off-screen in a stashed position
     * @param animationDuration    Duration of the animation to the new position
     * @param immediate            If true, PiP will move immediately to avoid keep clear areas
     */
    @VisibleForTesting
    void recalculatePipBounds(boolean stayAtAnchorPosition, boolean disallowStashing,
            int animationDuration, boolean immediate) {
        final Placement placement = mTvPipBoundsAlgorithm.getTvPipPlacement();

        final int stashType = disallowStashing ? STASH_TYPE_NONE : placement.getStashType();
        mTvPipBoundsState.setStashed(stashType);
        if (stayAtAnchorPosition) {
            cancelScheduledPlacement();
            applyPlacementBounds(placement.getAnchorBounds(), animationDuration);
        } else if (disallowStashing) {
            cancelScheduledPlacement();
            applyPlacementBounds(placement.getUnstashedBounds(), animationDuration);
        } else if (immediate) {
            boolean shouldStash = mUnstashRunnable != null || placement.getTriggerStash();
            cancelScheduledPlacement();
            applyPlacement(placement, shouldStash, animationDuration);
        } else {
            if (mCurrentPlacementBounds != null) {
                applyPlacementBounds(mCurrentPlacementBounds, animationDuration);
            }
            schedulePinnedStackPlacement(placement, animationDuration);
        }
    }

    private void schedulePinnedStackPlacement(@NonNull final Placement placement,
            int animationDuration) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: schedulePinnedStackPlacement() - pip bounds: %s",
                TAG, placement.getBounds().toShortString());

        if (mPendingPlacement != null && Objects.equals(mPendingPlacement.getBounds(),
                placement.getBounds())) {
            mPendingStash = mPendingStash || placement.getTriggerStash();
            return;
        }

        mPendingStash = placement.getStashType() != STASH_TYPE_NONE
                && (mPendingStash || placement.getTriggerStash());

        mMainHandler.removeCallbacks(mApplyPendingPlacementRunnable);
        mPendingPlacement = placement;
        mPendingPlacementAnimationDuration = animationDuration;
        mMainHandler.postAtTime(mApplyPendingPlacementRunnable,
                mClock.get() + POSITION_DEBOUNCE_TIMEOUT_MILLIS);
    }

    private void scheduleUnstashIfNeeded(final Placement placement) {
        if (mUnstashRunnable != null) {
            mMainHandler.removeCallbacks(mUnstashRunnable);
            mUnstashRunnable = null;
        }
        if (placement.getUnstashDestinationBounds() != null) {
            mUnstashRunnable = () -> {
                applyPlacementBounds(placement.getUnstashDestinationBounds(),
                        mResizeAnimationDuration);
                mUnstashRunnable = null;
            };
            mMainHandler.postAtTime(mUnstashRunnable, mClock.get() + mStashDurationMs);
        }
    }

    private void applyPendingPlacement() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: applyPendingPlacement()", TAG);
        if (mPendingPlacement != null) {
            applyPlacement(mPendingPlacement, mPendingStash, mPendingPlacementAnimationDuration);
            mPendingStash = false;
            mPendingPlacement = null;
        }
    }

    private void applyPlacement(@NonNull final Placement placement, boolean shouldStash,
            int animationDuration) {
        if (placement.getStashType() != STASH_TYPE_NONE && shouldStash) {
            scheduleUnstashIfNeeded(placement);
        }

        Rect bounds =
                mUnstashRunnable != null ? placement.getBounds() : placement.getUnstashedBounds();
        applyPlacementBounds(bounds, animationDuration);
    }

    void reset() {
        mCurrentPlacementBounds = null;
        mPipTargetBounds = null;
        cancelScheduledPlacement();
    }

    private void cancelScheduledPlacement() {
        mMainHandler.removeCallbacks(mApplyPendingPlacementRunnable);
        mPendingPlacement = null;

        if (mUnstashRunnable != null) {
            mMainHandler.removeCallbacks(mUnstashRunnable);
            mUnstashRunnable = null;
        }
    }

    private void applyPlacementBounds(Rect bounds, int animationDuration) {
        if (bounds == null) {
            return;
        }

        mCurrentPlacementBounds = bounds;
        Rect adjustedBounds = mTvPipBoundsAlgorithm.adjustBoundsForTemporaryDecor(bounds);
        movePipTo(adjustedBounds, animationDuration);
    }

    /** Animates the PiP to the given bounds with the given animation duration. */
    private void movePipTo(Rect bounds, int animationDuration) {
        if (Objects.equals(mPipTargetBounds, bounds)) {
            return;
        }

        mPipTargetBounds = bounds;
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: movePipTo() - new pip bounds: %s", TAG, bounds.toShortString());

        if (mListener != null) {
            mListener.onPipTargetBoundsChange(bounds, animationDuration);
        }
    }

    /**
     * Interface being notified of changes to the PiP bounds as calculated by
     * @link TvPipBoundsController}.
     */
    public interface PipBoundsListener {
        /**
         * Called when the calculated PiP bounds are changing.
         *
         * @param newTargetBounds The new bounds of the PiP.
         * @param animationDuration The animation duration for the PiP movement.
         */
        void onPipTargetBoundsChange(Rect newTargetBounds, int animationDuration);
    }
}
