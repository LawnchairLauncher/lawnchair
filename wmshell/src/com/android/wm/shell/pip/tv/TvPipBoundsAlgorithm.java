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

package com.android.wm.shell.pip.tv;

import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;

import static com.android.wm.shell.pip.tv.TvPipBoundsState.ORIENTATION_HORIZONTAL;
import static com.android.wm.shell.pip.tv.TvPipBoundsState.ORIENTATION_VERTICAL;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.Size;
import android.view.Gravity;

import androidx.annotation.NonNull;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipKeepClearAlgorithmInterface;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.pip.tv.TvPipKeepClearAlgorithm.Placement;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.Collections;
import java.util.Set;

/**
 * Contains pip bounds calculations that are specific to TV.
 */
public class TvPipBoundsAlgorithm extends PipBoundsAlgorithm {
    private static final String TAG = TvPipBoundsAlgorithm.class.getSimpleName();

    @NonNull
    private final TvPipBoundsState mTvPipBoundsState;

    private int mFixedExpandedHeightInPx;
    private int mFixedExpandedWidthInPx;

    private final TvPipKeepClearAlgorithm mKeepClearAlgorithm;

    public TvPipBoundsAlgorithm(Context context,
            @NonNull TvPipBoundsState tvPipBoundsState,
            @NonNull PipSnapAlgorithm pipSnapAlgorithm,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState,
            @NonNull SizeSpecSource sizeSpecSource) {
        super(context, tvPipBoundsState, pipSnapAlgorithm,
                new PipKeepClearAlgorithmInterface() {}, pipDisplayLayoutState, sizeSpecSource);
        this.mTvPipBoundsState = tvPipBoundsState;
        this.mKeepClearAlgorithm = new TvPipKeepClearAlgorithm();
        reloadResources(context);
    }

    private void reloadResources(Context context) {
        final Resources res = context.getResources();
        mFixedExpandedHeightInPx = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_pictureInPictureExpandedHorizontalHeight);
        mFixedExpandedWidthInPx = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_pictureInPictureExpandedVerticalWidth);
        mKeepClearAlgorithm.setPipAreaPadding(
                res.getDimensionPixelSize(R.dimen.pip_keep_clear_area_padding));
        mKeepClearAlgorithm.setMaxRestrictedDistanceFraction(
                res.getFraction(R.fraction.config_pipMaxRestrictedMoveDistance, 1, 1));
    }

    @Override
    public void onConfigurationChanged(Context context) {
        super.onConfigurationChanged(context);
        reloadResources(context);
    }

    /** Returns the destination bounds to place the PIP window on entry. */
    @Override
    public Rect getEntryDestinationBounds() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: getEntryDestinationBounds()", TAG);

        updateExpandedPipSize();
        final boolean isPipExpanded = mTvPipBoundsState.isTvExpandedPipSupported()
                && mTvPipBoundsState.getDesiredTvExpandedAspectRatio() != 0
                && !mTvPipBoundsState.isTvPipManuallyCollapsed();
        if (isPipExpanded) {
            updateGravityOnExpansionToggled(/* expanding= */ isPipExpanded);
        }
        mTvPipBoundsState.setTvPipExpanded(isPipExpanded);
        return adjustBoundsForTemporaryDecor(getTvPipPlacement().getBounds());
    }

    @Override
    public Rect getEntryDestinationBoundsIgnoringKeepClearAreas() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: getEntryDestinationBoundsIgnoringKeepClearAreas()", TAG);

        updateExpandedPipSize();
        final boolean isPipExpanded = mTvPipBoundsState.isTvExpandedPipSupported()
                && mTvPipBoundsState.getDesiredTvExpandedAspectRatio() != 0
                && !mTvPipBoundsState.isTvPipManuallyCollapsed();
        if (isPipExpanded) {
            updateGravityOnExpansionToggled(/* expanding= */ isPipExpanded);
        }
        mTvPipBoundsState.setTvPipExpanded(isPipExpanded);
        return adjustBoundsForTemporaryDecor(getTvPipPlacement(Collections.emptySet(),
                Collections.emptySet()).getUnstashedBounds());
    }

    /** Returns the current bounds adjusted to the new aspect ratio, if valid. */
    @Override
    public Rect getAdjustedDestinationBounds(Rect currentBounds, float newAspectRatio) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: getAdjustedDestinationBounds: %f", TAG, newAspectRatio);
        return adjustBoundsForTemporaryDecor(getTvPipPlacement().getBounds());
    }

    Rect adjustBoundsForTemporaryDecor(Rect bounds) {
        Rect boundsWithDecor = new Rect(bounds);
        Insets decorInset = mTvPipBoundsState.getPipMenuTemporaryDecorInsets();
        Insets pipDecorReverseInsets = Insets.subtract(Insets.NONE, decorInset);
        boundsWithDecor.inset(decorInset);
        Gravity.apply(mTvPipBoundsState.getTvPipGravity(),
                boundsWithDecor.width(), boundsWithDecor.height(), bounds, boundsWithDecor);

        // remove temporary decoration again
        boundsWithDecor.inset(pipDecorReverseInsets);
        return boundsWithDecor;
    }

    /**
     * Calculates the PiP bounds.
     */
    @NonNull
    public Placement getTvPipPlacement() {
        final Set<Rect> restrictedKeepClearAreas = mTvPipBoundsState.getRestrictedKeepClearAreas();
        final Set<Rect> unrestrictedKeepClearAreas =
                mTvPipBoundsState.getUnrestrictedKeepClearAreas();

        return getTvPipPlacement(restrictedKeepClearAreas, unrestrictedKeepClearAreas);
    }

    /**
     * Calculates the PiP bounds.
     */
    @NonNull
    private Placement getTvPipPlacement(Set<Rect> restrictedKeepClearAreas,
            Set<Rect> unrestrictedKeepClearAreas) {
        final Size pipSize = getPipSize();
        final Rect displayBounds = mTvPipBoundsState.getDisplayBounds();
        final Size screenSize = new Size(displayBounds.width(), displayBounds.height());
        final Rect insetBounds = new Rect();
        getInsetBounds(insetBounds);

        mKeepClearAlgorithm.setGravity(mTvPipBoundsState.getTvPipGravity());
        mKeepClearAlgorithm.setScreenSize(screenSize);
        mKeepClearAlgorithm.setMovementBounds(insetBounds);
        mKeepClearAlgorithm.setStashOffset(mTvPipBoundsState.getStashOffset());
        mKeepClearAlgorithm.setPipPermanentDecorInsets(
                mTvPipBoundsState.getPipMenuPermanentDecorInsets());

        final Placement placement = mKeepClearAlgorithm.calculatePipPosition(
                pipSize,
                restrictedKeepClearAreas,
                unrestrictedKeepClearAreas);

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: screenSize: %s", TAG, screenSize);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: stashOffset: %d", TAG, mTvPipBoundsState.getStashOffset());
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: insetBounds: %s", TAG, insetBounds.toShortString());
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: pipSize: %s", TAG, pipSize);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: gravity: %s", TAG, Gravity.toString(mTvPipBoundsState.getTvPipGravity()));
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: restrictedKeepClearAreas: %s", TAG, restrictedKeepClearAreas);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: unrestrictedKeepClearAreas: %s", TAG, unrestrictedKeepClearAreas);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: placement: %s", TAG, placement);

        return placement;
    }

    void updateGravityOnExpansionToggled(boolean expanding) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: updateGravity, expanding: %b, fixedExpandedOrientation: %d",
                TAG, expanding, mTvPipBoundsState.getTvFixedPipOrientation());

        int currentX = mTvPipBoundsState.getTvPipGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
        int currentY = mTvPipBoundsState.getTvPipGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        int previousCollapsedX = mTvPipBoundsState.getTvPipPreviousCollapsedGravity()
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        int previousCollapsedY = mTvPipBoundsState.getTvPipPreviousCollapsedGravity()
                & Gravity.VERTICAL_GRAVITY_MASK;

        int updatedGravity;
        if (expanding) {
            if (!mTvPipBoundsState.isTvPipExpanded()) {
                // Save collapsed gravity.
                mTvPipBoundsState.setTvPipPreviousCollapsedGravity(
                        mTvPipBoundsState.getTvPipGravity());
            }

            if (mTvPipBoundsState.getTvFixedPipOrientation() == ORIENTATION_HORIZONTAL) {
                updatedGravity = Gravity.CENTER_HORIZONTAL | currentY;
            } else {
                updatedGravity = currentX | Gravity.CENTER_VERTICAL;
            }
        } else {
            // Collapse to the edge that the user moved to before.
            if (mTvPipBoundsState.getTvFixedPipOrientation() == ORIENTATION_HORIZONTAL) {
                updatedGravity = previousCollapsedX | currentY;
            } else {
                updatedGravity = currentX | previousCollapsedY;
            }
        }
        mTvPipBoundsState.setTvPipGravity(updatedGravity);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: new gravity: %s", TAG, Gravity.toString(updatedGravity));
    }

    /**
     * @return true if the gravity changed
     */
    boolean updateGravity(int keycode) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: updateGravity, keycode: %d", TAG, keycode);

        // Check if position change is valid.
        if (mTvPipBoundsState.isTvPipExpanded()) {
            int fixedOrientation = mTvPipBoundsState.getTvFixedPipOrientation();
            if (fixedOrientation == ORIENTATION_VERTICAL
                    && (keycode == KEYCODE_DPAD_UP || keycode == KEYCODE_DPAD_DOWN)
                    || fixedOrientation == ORIENTATION_HORIZONTAL
                    && (keycode == KEYCODE_DPAD_RIGHT || keycode == KEYCODE_DPAD_LEFT)) {
                return false;
            }
        }

        int updatedX = mTvPipBoundsState.getTvPipGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
        int updatedY = mTvPipBoundsState.getTvPipGravity() & Gravity.VERTICAL_GRAVITY_MASK;

        switch (keycode) {
            case KEYCODE_DPAD_UP:
                updatedY = Gravity.TOP;
                break;
            case KEYCODE_DPAD_DOWN:
                updatedY = Gravity.BOTTOM;
                break;
            case KEYCODE_DPAD_LEFT:
                updatedX = Gravity.LEFT;
                break;
            case KEYCODE_DPAD_RIGHT:
                updatedX = Gravity.RIGHT;
                break;
            default:
                // NOOP - unsupported keycode
        }

        int updatedGravity = updatedX | updatedY;

        if (updatedGravity != mTvPipBoundsState.getTvPipGravity()) {
            mTvPipBoundsState.setTvPipGravity(updatedGravity);
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: updateGravity, new gravity: %s", TAG, Gravity.toString(updatedGravity));
            return true;
        }
        return false;
    }

    private Size getPipSize() {
        final boolean isExpanded =
                mTvPipBoundsState.isTvExpandedPipSupported() && mTvPipBoundsState.isTvPipExpanded()
                        && mTvPipBoundsState.getDesiredTvExpandedAspectRatio() != 0;
        if (isExpanded) {
            return mTvPipBoundsState.getTvExpandedSize();
        } else {
            final Rect normalBounds = getNormalBounds();
            return new Size(normalBounds.width(), normalBounds.height());
        }
    }

    /**
     * Updates {@link TvPipBoundsState#getTvExpandedSize()} based on
     * {@link TvPipBoundsState#getDesiredTvExpandedAspectRatio()}, the screen size.
     */
    void updateExpandedPipSize() {
        final DisplayLayout displayLayout = mTvPipBoundsState.getDisplayLayout();
        final float expandedRatio =
                mTvPipBoundsState.getDesiredTvExpandedAspectRatio(); // width / height
        final Insets pipDecorations = mTvPipBoundsState.getPipMenuPermanentDecorInsets();

        final Size expandedSize;
        if (expandedRatio == 0) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: updateExpandedPipSize(): Expanded mode aspect ratio"
                            + " of 0 not supported", TAG);
            return;
        } else if (expandedRatio < 1) {
            // vertical
            if (mTvPipBoundsState.getTvFixedPipOrientation() == ORIENTATION_HORIZONTAL) {
                expandedSize = mTvPipBoundsState.getTvExpandedSize();
            } else {
                int maxHeight = displayLayout.height()
                        - (2 * mPipDisplayLayoutState.getScreenEdgeInsets().y)
                        - pipDecorations.top - pipDecorations.bottom;
                float aspectRatioHeight = mFixedExpandedWidthInPx / expandedRatio;

                if (maxHeight > aspectRatioHeight) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Accommodate aspect ratio", TAG);
                    expandedSize = new Size(mFixedExpandedWidthInPx, (int) aspectRatioHeight);
                } else {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Aspect ratio is too extreme, use max size", TAG);
                    expandedSize = new Size(mFixedExpandedWidthInPx, maxHeight);
                }
            }
        } else {
            // horizontal
            if (mTvPipBoundsState.getTvFixedPipOrientation() == ORIENTATION_VERTICAL) {
                expandedSize = mTvPipBoundsState.getTvExpandedSize();
            } else {
                int maxWidth = displayLayout.width()
                        - (2 * mPipDisplayLayoutState.getScreenEdgeInsets().x)
                        - pipDecorations.left - pipDecorations.right;
                float aspectRatioWidth = mFixedExpandedHeightInPx * expandedRatio;
                if (maxWidth > aspectRatioWidth) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Accommodate aspect ratio", TAG);
                    expandedSize = new Size((int) aspectRatioWidth, mFixedExpandedHeightInPx);
                } else {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Aspect ratio is too extreme, use max size", TAG);
                    expandedSize = new Size(maxWidth, mFixedExpandedHeightInPx);
                }
            }
        }

        mTvPipBoundsState.setTvExpandedSize(expandedSize);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: updateExpandedPipSize(): expanded size, width: %d, height: %d",
                TAG, expandedSize.getWidth(), expandedSize.getHeight());
    }
}
