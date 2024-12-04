/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.common.pip;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.io.PrintWriter;

/**
 * Calculates the default, normal, entry, inset and movement bounds of the PIP.
 */
public class PipBoundsAlgorithm {

    private static final String TAG = PipBoundsAlgorithm.class.getSimpleName();
    private static final float INVALID_SNAP_FRACTION = -1f;

    // The same value (with the same name) is used in Launcher.
    private static final float PIP_ASPECT_RATIO_MISMATCH_THRESHOLD = 0.01f;

    @NonNull private final PipBoundsState mPipBoundsState;
    @NonNull protected final PipDisplayLayoutState mPipDisplayLayoutState;
    @NonNull protected final SizeSpecSource mSizeSpecSource;
    private final PipSnapAlgorithm mSnapAlgorithm;
    private final PipKeepClearAlgorithmInterface mPipKeepClearAlgorithm;

    private float mDefaultAspectRatio;
    private float mMinAspectRatio;
    private float mMaxAspectRatio;
    private int mDefaultStackGravity;

    public PipBoundsAlgorithm(Context context, @NonNull PipBoundsState pipBoundsState,
            @NonNull PipSnapAlgorithm pipSnapAlgorithm,
            @NonNull PipKeepClearAlgorithmInterface pipKeepClearAlgorithm,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState,
            @NonNull SizeSpecSource sizeSpecSource) {
        mPipBoundsState = pipBoundsState;
        mSnapAlgorithm = pipSnapAlgorithm;
        mPipKeepClearAlgorithm = pipKeepClearAlgorithm;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mSizeSpecSource = sizeSpecSource;
        reloadResources(context);
        // Initialize the aspect ratio to the default aspect ratio.  Don't do this in reload
        // resources as it would clobber mAspectRatio when entering PiP from fullscreen which
        // triggers a configuration change and the resources to be reloaded.
        mPipBoundsState.setAspectRatio(mDefaultAspectRatio);
    }

    /**
     * TODO: move the resources to SysUI package.
     */
    private void reloadResources(Context context) {
        final Resources res = context.getResources();
        mDefaultAspectRatio = res.getFloat(
                R.dimen.config_pictureInPictureDefaultAspectRatio);
        mDefaultStackGravity = res.getInteger(
                R.integer.config_defaultPictureInPictureGravity);
        mMinAspectRatio = res.getFloat(
                com.android.internal.R.dimen.config_pictureInPictureMinAspectRatio);
        mMaxAspectRatio = res.getFloat(
                com.android.internal.R.dimen.config_pictureInPictureMaxAspectRatio);
    }

    /**
     * The {@link PipSnapAlgorithm} is couple on display bounds
     * @return {@link PipSnapAlgorithm}.
     */
    public PipSnapAlgorithm getSnapAlgorithm() {
        return mSnapAlgorithm;
    }

    /** Responds to configuration change. */
    public void onConfigurationChanged(Context context) {
        reloadResources(context);
    }

    /** Returns the normal bounds (i.e. the default entry bounds). */
    public Rect getNormalBounds() {
        // The normal bounds are the default bounds adjusted to the current aspect ratio.
        return transformBoundsToAspectRatioIfValid(getDefaultBounds(),
                mPipBoundsState.getAspectRatio(), false /* useCurrentMinEdgeSize */,
                false /* useCurrentSize */);
    }

    /** Returns the default bounds. */
    public Rect getDefaultBounds() {
        return getDefaultBounds(INVALID_SNAP_FRACTION, null /* size */);
    }

    /**
     * Returns the destination bounds to place the PIP window on entry.
     * If there are any keep clear areas registered, the position will try to avoid occluding them.
     */
    public Rect getEntryDestinationBounds() {
        Rect entryBounds = getEntryDestinationBoundsIgnoringKeepClearAreas();
        Rect insets = new Rect();
        getInsetBounds(insets);
        return mPipKeepClearAlgorithm.findUnoccludedPosition(entryBounds,
                mPipBoundsState.getRestrictedKeepClearAreas(),
                mPipBoundsState.getUnrestrictedKeepClearAreas(), insets);
    }

    /** Returns the destination bounds to place the PIP window on entry. */
    public Rect getEntryDestinationBoundsIgnoringKeepClearAreas() {
        final PipBoundsState.PipReentryState reentryState = mPipBoundsState.getReentryState();

        final Rect destinationBounds = getDefaultBounds();
        if (reentryState != null) {
            final Size scaledBounds = new Size(
                    Math.round(mPipBoundsState.getMaxSize().x * reentryState.getBoundsScale()),
                    Math.round(mPipBoundsState.getMaxSize().y * reentryState.getBoundsScale()));
            destinationBounds.set(getDefaultBounds(reentryState.getSnapFraction(), scaledBounds));
        }

        final boolean useCurrentSize = reentryState != null;
        Rect aspectRatioBounds = transformBoundsToAspectRatioIfValid(destinationBounds,
                mPipBoundsState.getAspectRatio(), false /* useCurrentMinEdgeSize */,
                useCurrentSize);
        return aspectRatioBounds;
    }

    /** Returns the current bounds adjusted to the new aspect ratio, if valid. */
    public Rect getAdjustedDestinationBounds(Rect currentBounds, float newAspectRatio) {
        return transformBoundsToAspectRatioIfValid(currentBounds, newAspectRatio,
                true /* useCurrentMinEdgeSize */, false /* useCurrentSize */);
    }

    /**
     *
     * Get the smallest/most minimal size allowed.
     */
    public Size getMinimalSize(ActivityInfo activityInfo) {
        if (activityInfo == null || activityInfo.windowLayout == null) {
            return null;
        }
        final ActivityInfo.WindowLayout windowLayout = activityInfo.windowLayout;
        // -1 will be populated if an activity specifies defaultWidth/defaultHeight in <layout>
        // without minWidth/minHeight
        if (windowLayout.minWidth > 0 && windowLayout.minHeight > 0) {
            // If either dimension is smaller than the allowed minimum, adjust them
            // according to mOverridableMinSize
            return new Size(
                    Math.max(windowLayout.minWidth, getOverrideMinEdgeSize()),
                    Math.max(windowLayout.minHeight, getOverrideMinEdgeSize()));
        }
        return null;
    }

    /**
     * Returns the source hint rect if it is valid (if provided and is contained by the current
     * task bounds).
     */
    public static Rect getValidSourceHintRect(PictureInPictureParams params, Rect sourceBounds) {
        final Rect sourceHintRect = params != null && params.hasSourceBoundsHint()
                ? params.getSourceRectHint()
                : null;
        if (sourceHintRect != null && sourceBounds.contains(sourceHintRect)) {
            return sourceHintRect;
        }
        return null;
    }


    /**
     * Returns the source hint rect if it is valid (if provided and is contained by the current
     * task bounds, while not smaller than the destination bounds).
     */
    @Nullable
    public static Rect getValidSourceHintRect(PictureInPictureParams params, Rect sourceBounds,
            Rect destinationBounds) {
        Rect sourceRectHint = getValidSourceHintRect(params, sourceBounds);
        if (!isSourceRectHintValidForEnterPip(sourceRectHint, destinationBounds)) {
            sourceRectHint = null;
        }
        return sourceRectHint;
    }

    /**
     * This is a situation in which the source rect hint on at least one axis is smaller
     * than the destination bounds, which represents a problem because we would have to scale
     * up that axis to fit the bounds. So instead, just fallback to the non-source hint
     * animation in this case.
     *
     * @return {@code false} if the given source is too small to use for the entering animation.
     */
    public static boolean isSourceRectHintValidForEnterPip(Rect sourceRectHint,
            Rect destinationBounds) {
        if (sourceRectHint == null || sourceRectHint.isEmpty()) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "isSourceRectHintValidForEnterPip=false, empty hint");
            return false;
        }
        if (sourceRectHint.width() <= destinationBounds.width()
                || sourceRectHint.height() <= destinationBounds.height()) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "isSourceRectHintValidForEnterPip=false, hint(%s) is smaller"
                            + " than destination(%s)", sourceRectHint, destinationBounds);
            return false;
        }
        final float reportedRatio = destinationBounds.width() / (float) destinationBounds.height();
        final float inferredRatio = sourceRectHint.width() / (float) sourceRectHint.height();
        if (Math.abs(reportedRatio - inferredRatio) > PIP_ASPECT_RATIO_MISMATCH_THRESHOLD) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "isSourceRectHintValidForEnterPip=false, hint(%s) does not match"
                            + " destination(%s) aspect ratio", sourceRectHint, destinationBounds);
            return false;
        }
        return true;
    }

    public float getDefaultAspectRatio() {
        return mDefaultAspectRatio;
    }

    /**
     *
     * Give the aspect ratio if the supplied PiP params have one, or else return default.
     */
    public float getAspectRatioOrDefault(
            @android.annotation.Nullable PictureInPictureParams params) {
        return params != null && params.hasSetAspectRatio()
                ? params.getAspectRatioFloat()
                : getDefaultAspectRatio();
    }

    /**
     * @return whether the given aspectRatio is valid.
     */
    public boolean isValidPictureInPictureAspectRatio(float aspectRatio) {
        return Float.compare(mMinAspectRatio, aspectRatio) <= 0
                && Float.compare(aspectRatio, mMaxAspectRatio) <= 0;
    }

    private Rect transformBoundsToAspectRatioIfValid(Rect bounds, float aspectRatio,
            boolean useCurrentMinEdgeSize, boolean useCurrentSize) {
        final Rect destinationBounds = new Rect(bounds);
        if (isValidPictureInPictureAspectRatio(aspectRatio)) {
            transformBoundsToAspectRatio(destinationBounds, aspectRatio,
                    useCurrentMinEdgeSize, useCurrentSize);
        }
        return destinationBounds;
    }

    /**
     * Set the current bounds (or the default bounds if there are no current bounds) with the
     * specified aspect ratio.
     */
    public void transformBoundsToAspectRatio(Rect stackBounds, float aspectRatio,
            boolean useCurrentMinEdgeSize, boolean useCurrentSize) {
        // Save the snap fraction and adjust the size based on the new aspect ratio.
        final float snapFraction = mSnapAlgorithm.getSnapFraction(stackBounds,
                getMovementBounds(stackBounds), mPipBoundsState.getStashedState());

        final Size size;
        if (useCurrentMinEdgeSize || useCurrentSize) {
            // Use the existing size but adjusted to the new aspect ratio.
            size = mSizeSpecSource.getSizeForAspectRatio(
                    new Size(stackBounds.width(), stackBounds.height()), aspectRatio);
        } else {
            size = mSizeSpecSource.getDefaultSize(aspectRatio);
        }

        final int left = (int) (stackBounds.centerX() - size.getWidth() / 2f);
        final int top = (int) (stackBounds.centerY() - size.getHeight() / 2f);
        stackBounds.set(left, top, left + size.getWidth(), top + size.getHeight());
        mSnapAlgorithm.applySnapFraction(stackBounds, getMovementBounds(stackBounds), snapFraction);
    }

    /**
     * @return the default bounds to show the PIP, if a {@param snapFraction} and {@param size} are
     * provided, then it will apply the default bounds to the provided snap fraction and size.
     */
    private Rect getDefaultBounds(float snapFraction, Size size) {
        final Rect defaultBounds = new Rect();
        if (snapFraction != INVALID_SNAP_FRACTION && size != null) {
            // The default bounds are the given size positioned at the given snap fraction.
            defaultBounds.set(0, 0, size.getWidth(), size.getHeight());
            final Rect movementBounds = getMovementBounds(defaultBounds);
            mSnapAlgorithm.applySnapFraction(defaultBounds, movementBounds, snapFraction);
            return defaultBounds;
        }

        // Calculate the default size.
        final Size defaultSize;
        final Rect insetBounds = new Rect();
        getInsetBounds(insetBounds);

        // Calculate the default size
        defaultSize = mSizeSpecSource.getDefaultSize(mDefaultAspectRatio);

        // Now that we have the default size, apply the snap fraction if valid or position the
        // bounds using the default gravity.
        if (snapFraction != INVALID_SNAP_FRACTION) {
            defaultBounds.set(0, 0, defaultSize.getWidth(), defaultSize.getHeight());
            final Rect movementBounds = getMovementBounds(defaultBounds);
            mSnapAlgorithm.applySnapFraction(defaultBounds, movementBounds, snapFraction);
        } else {
            Gravity.apply(mDefaultStackGravity, defaultSize.getWidth(), defaultSize.getHeight(),
                    insetBounds, 0, Math.max(
                            mPipBoundsState.isImeShowing() ? mPipBoundsState.getImeHeight() : 0,
                            mPipBoundsState.isShelfShowing()
                                    ? mPipBoundsState.getShelfHeight() : 0), defaultBounds);
        }
        return defaultBounds;
    }

    /**
     * Populates the bounds on the screen that the PIP can be visible in.
     */
    public void getInsetBounds(Rect outRect) {
        outRect.set(mPipDisplayLayoutState.getInsetBounds());
    }

    private int getOverrideMinEdgeSize() {
        return mSizeSpecSource.getOverrideMinEdgeSize();
    }

    /**
     * @return the movement bounds for the given stackBounds and the current state of the
     *         controller.
     */
    public Rect getMovementBounds(Rect stackBounds) {
        return getMovementBounds(stackBounds, true /* adjustForIme */);
    }

    /**
     * @return the movement bounds for the given stackBounds and the current state of the
     *         controller.
     */
    public Rect getMovementBounds(Rect stackBounds, boolean adjustForIme) {
        final Rect movementBounds = new Rect();
        getInsetBounds(movementBounds);

        // Apply the movement bounds adjustments based on the current state.
        getMovementBounds(stackBounds, movementBounds, movementBounds,
                (adjustForIme && mPipBoundsState.isImeShowing())
                        ? mPipBoundsState.getImeHeight() : 0);

        return movementBounds;
    }

    /**
     * Adjusts movementBoundsOut so that it is the movement bounds for the given stackBounds.
     */
    public void getMovementBounds(Rect stackBounds, Rect insetBounds, Rect movementBoundsOut,
            int bottomOffset) {
        // Adjust the right/bottom to ensure the stack bounds never goes offscreen
        movementBoundsOut.set(insetBounds);
        movementBoundsOut.right = Math.max(insetBounds.left, insetBounds.right
                - stackBounds.width());
        movementBoundsOut.bottom = Math.max(insetBounds.top, insetBounds.bottom
                - stackBounds.height());
        movementBoundsOut.bottom -= bottomOffset;
    }

    /**
     * @return the default snap fraction to apply instead of the default gravity when calculating
     *         the default stack bounds when first entering PiP.
     */
    public float getSnapFraction(Rect stackBounds) {
        return getSnapFraction(stackBounds, getMovementBounds(stackBounds));
    }

    /**
     * @return the default snap fraction to apply instead of the default gravity when calculating
     *         the default stack bounds when first entering PiP.
     */
    public float getSnapFraction(Rect stackBounds, Rect movementBounds) {
        return mSnapAlgorithm.getSnapFraction(stackBounds, movementBounds);
    }

    /**
     * Applies the given snap fraction to the given stack bounds.
     */
    public void applySnapFraction(Rect stackBounds, float snapFraction) {
        final Rect movementBounds = getMovementBounds(stackBounds);
        mSnapAlgorithm.applySnapFraction(stackBounds, movementBounds, snapFraction);
    }

    /**
     * @return the pixels for a given dp value.
     */
    private int dpToPx(float dpValue, DisplayMetrics dm) {
        return PipUtils.dpToPx(dpValue, dm);
    }

    /**
     * @return the normal bounds adjusted so that they fit the menu actions.
     */
    public Rect adjustNormalBoundsToFitMenu(@NonNull Rect normalBounds,
            @Nullable Size minMenuSize) {
        if (minMenuSize == null) {
            return normalBounds;
        }
        if (normalBounds.width() >= minMenuSize.getWidth()
                && normalBounds.height() >= minMenuSize.getHeight()) {
            // The normal bounds can fit the menu as is, no need to adjust the bounds.
            return normalBounds;
        }
        final Rect adjustedNormalBounds = new Rect();
        final boolean needsWidthAdj = minMenuSize.getWidth() > normalBounds.width();
        final boolean needsHeightAdj = minMenuSize.getHeight() > normalBounds.height();
        final int adjWidth;
        final int adjHeight;
        if (needsWidthAdj && needsHeightAdj) {
            // Both the width and the height are too small - find the edge that needs the larger
            // adjustment and scale that edge. The other edge will scale beyond the minMenuSize
            // when the aspect ratio is applied.
            final float widthScaleFactor =
                    ((float) (minMenuSize.getWidth())) / ((float) (normalBounds.width()));
            final float heightScaleFactor =
                    ((float) (minMenuSize.getHeight())) / ((float) (normalBounds.height()));
            if (widthScaleFactor > heightScaleFactor) {
                adjWidth = minMenuSize.getWidth();
                adjHeight = Math.round(adjWidth / mPipBoundsState.getAspectRatio());
            } else {
                adjHeight = minMenuSize.getHeight();
                adjWidth = Math.round(adjHeight * mPipBoundsState.getAspectRatio());
            }
        } else if (needsWidthAdj) {
            // Width is too small - use the min menu size width instead.
            adjWidth = minMenuSize.getWidth();
            adjHeight = Math.round(adjWidth / mPipBoundsState.getAspectRatio());
        } else {
            // Height is too small - use the min menu size height instead.
            adjHeight = minMenuSize.getHeight();
            adjWidth = Math.round(adjHeight * mPipBoundsState.getAspectRatio());
        }
        adjustedNormalBounds.set(0, 0, adjWidth, adjHeight);
        // Make sure the bounds conform to the aspect ratio and min edge size.
        transformBoundsToAspectRatio(adjustedNormalBounds,
                mPipBoundsState.getAspectRatio(), true /* useCurrentMinEdgeSize */,
                true /* useCurrentSize */);
        return adjustedNormalBounds;
    }

    /**
     * Dumps internal states.
     */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mDefaultAspectRatio=" + mDefaultAspectRatio);
        pw.println(innerPrefix + "mMinAspectRatio=" + mMinAspectRatio);
        pw.println(innerPrefix + "mMaxAspectRatio=" + mMaxAspectRatio);
        pw.println(innerPrefix + "mDefaultStackGravity=" + mDefaultStackGravity);
        pw.println(innerPrefix + "mSnapAlgorithm" + mSnapAlgorithm);
    }
}
