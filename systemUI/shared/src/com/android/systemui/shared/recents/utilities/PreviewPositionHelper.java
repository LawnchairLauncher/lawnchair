package com.android.systemui.shared.recents.utilities;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.wm.shell.shared.split.SplitBounds;

/**
 * Utility class to position the thumbnail in the TaskView
 */
public class PreviewPositionHelper {

    public static final float MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT = 0.1f;

    private final Matrix mMatrix = new Matrix();
    private boolean mIsOrientationChanged;
    /**
     * Only used when this helper is being used for an app in split screen. Refers to the position
     * of the app in the pair.
     * See {@link com.android.wm.shell.shared.split.SplitScreenConstants#@SplitPosition}
     */
    private int mSplitPosition = SPLIT_POSITION_UNDEFINED;
    /**
     * Guarded by enableFlexibleTwoAppSplit() flag, but this class doesn't have access so the
     * caller is responsible for checking. If the flag is disabled this will be null
     */
    @Nullable
    private SplitBounds mSplitBounds;

    public Matrix getMatrix() {
        return mMatrix;
    }

    public void setOrientationChanged(boolean orientationChanged) {
        mIsOrientationChanged = orientationChanged;
    }

    public boolean isOrientationChanged() {
        return mIsOrientationChanged;
    }

    // TODO: b/428707131 - Pass-in visibleRect of thumbnail, removing setSplitBounds method.
    /**
     * Updates the matrix based on the provided parameters
     */
    public void updateThumbnailMatrix(Rect thumbnailBounds, @NonNull ThumbnailData thumbnailData,
            int canvasWidth, int canvasHeight, boolean isLargeScreen, int currentRotation,
            boolean isRtl, int deviceDensityDpi) {
        boolean isRotated = false;
        boolean isOrientationDifferent;

        int thumbnailRotation = thumbnailData.rotation;
        int deltaRotate = getRotationDelta(currentRotation, thumbnailRotation);
        RectF thumbnailClipHint = new RectF();
        float scale = thumbnailData.scale;
        final float thumbnailScale;
        float rotationYClipHint = 0;

        // Landscape vs portrait change.
        // Note: Disable rotation in grid layout.
        boolean windowingModeSupportsRotation =
                thumbnailData.windowingMode == WINDOWING_MODE_FULLSCREEN && !isLargeScreen;
        isOrientationDifferent = isOrientationChange(deltaRotate)
                && windowingModeSupportsRotation;
        if (canvasWidth == 0 || canvasHeight == 0 || scale == 0) {
            // If we haven't measured , skip the thumbnail drawing and only draw the background
            // color
            thumbnailScale = 0f;
        } else {
            // Rotate the screenshot if not in multi-window mode
            isRotated = deltaRotate > 0 && windowingModeSupportsRotation;

            float surfaceWidth = thumbnailBounds.width() / scale;
            float surfaceHeight = thumbnailBounds.height() / scale;
            float availableWidth = surfaceWidth;
            float availableHeight = surfaceHeight;

            float canvasAspect = canvasWidth / (float) canvasHeight;
            float availableAspect = isRotated
                    ? availableHeight / availableWidth
                    : availableWidth / availableHeight;
            boolean isAspectLargelyDifferent =
                    Utilities.isRelativePercentDifferenceGreaterThan(canvasAspect,
                            availableAspect, MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT);
            if (isRotated && !shouldRotateThumbnail(isAspectLargelyDifferent)) {
                // Do not rotate thumbnail if it would not improve fit
                isRotated = false;
                isOrientationDifferent = false;
            }

            if (isAspectLargelyDifferent) {
                // Crop letterbox insets if insets isn't already clipped
                thumbnailClipHint.left = thumbnailData.letterboxInsets.left;
                thumbnailClipHint.right = thumbnailData.letterboxInsets.right;
                thumbnailClipHint.top = thumbnailData.letterboxInsets.top;
                thumbnailClipHint.bottom = thumbnailData.letterboxInsets.bottom;
                availableWidth = surfaceWidth
                        - (thumbnailClipHint.left + thumbnailClipHint.right);
                availableHeight = surfaceHeight
                        - (thumbnailClipHint.top + thumbnailClipHint.bottom);
            }

            final float targetW, targetH;
            if (isOrientationDifferent) {
                targetW = canvasHeight;
                targetH = canvasWidth;
            } else {
                targetW = canvasWidth;
                targetH = canvasHeight;
            }
            float targetAspect = targetW / targetH;

            // Update the clipHint such that
            //   > the final clipped position has same aspect ratio as requested by canvas
            //   > first fit the width and crop the extra height
            //   > if that will leave empty space, fit the height and crop the width instead
            float croppedWidth = availableWidth;
            float croppedHeight = croppedWidth / targetAspect;
            if (croppedHeight > availableHeight) {
                croppedHeight = availableHeight;
                if (croppedHeight < targetH) {
                    croppedHeight = Math.min(targetH, surfaceHeight);
                }
                croppedWidth = croppedHeight * targetAspect;

                // One last check in case the task aspect radio messed up something
                if (croppedWidth > surfaceWidth) {
                    croppedWidth = surfaceWidth;
                    croppedHeight = croppedWidth / targetAspect;
                }
            }

            // Update the clip hints. Align to 0,0, crop the remaining.
            if (isRtl) {
                thumbnailClipHint.left += availableWidth - croppedWidth;
                if (thumbnailClipHint.right < 0) {
                    thumbnailClipHint.left += thumbnailClipHint.right;
                    thumbnailClipHint.right = 0;
                }
            } else {
                thumbnailClipHint.right += availableWidth - croppedWidth;
                if (thumbnailClipHint.left < 0) {
                    thumbnailClipHint.right += thumbnailClipHint.left;
                    thumbnailClipHint.left = 0;
                }
            }
            thumbnailClipHint.bottom += availableHeight - croppedHeight;
            if (thumbnailClipHint.top < 0) {
                thumbnailClipHint.bottom += thumbnailClipHint.top;
                thumbnailClipHint.top = 0;
            } else if (thumbnailClipHint.bottom < 0) {
                thumbnailClipHint.top += thumbnailClipHint.bottom;
                thumbnailClipHint.bottom = 0;
            }

            final float densityScale;
            if (thumbnailData.getThumbnail() != null
                    && thumbnailData.getThumbnail().getDensity() != Bitmap.DENSITY_NONE
                    && deviceDensityDpi > 0) {
                densityScale = (float) deviceDensityDpi / thumbnailData.getThumbnail().getDensity();
            } else {
                densityScale = 1f;
            }

            thumbnailScale = targetW / (croppedWidth * scale * densityScale);
            if (mSplitBounds != null
                    && mSplitBounds.snapPosition == SNAP_TO_2_10_90
                    && mSplitPosition == SPLIT_POSITION_TOP_OR_LEFT) {
                if (mSplitBounds.appsStackedVertically) {
                    thumbnailClipHint.top += availableHeight - croppedHeight;
                } else {
                    thumbnailClipHint.left += availableWidth - croppedWidth;
                }
            }
            // Adjust translation for specific horizontal split-screen scenarios
            // where clipping occurs.
            if (shouldApplySplitScreenClippingAdjustment(deltaRotate)) {
                rotationYClipHint = availableWidth - croppedWidth;
            }
        }

        if (isRotated) {
            setThumbnailRotation(deltaRotate, thumbnailBounds, rotationYClipHint);
        } else {
            mMatrix.setTranslate(
                    -thumbnailClipHint.left * scale,
                    -thumbnailClipHint.top * scale);
        }

        mMatrix.postScale(thumbnailScale, thumbnailScale);
        mIsOrientationChanged = isOrientationDifferent;
    }

    private int getRotationDelta(int oldRotation, int newRotation) {
        int delta = newRotation - oldRotation;
        if (delta < 0) delta += 4;
        return delta;
    }

    /**
     * @param deltaRotation the number of 90 degree turns from the current orientation
     * @return {@code true} if the change in rotation results in a shift from landscape to
     * portrait or vice versa, {@code false} otherwise
     */
    private boolean isOrientationChange(int deltaRotation) {
        return deltaRotation == ROTATION_90 || deltaRotation == ROTATION_270;
    }

    private void setThumbnailRotation(int deltaRotate, Rect thumbnailPosition,
            float rotationYClipHint) {
        mMatrix.setRotate(90 * deltaRotate);

        float translateX = 0;
        float translateY = 0;
        switch (deltaRotate) { /* Counter-clockwise */
            case ROTATION_90:
                translateX = thumbnailPosition.height();
                break;
            case ROTATION_270:
                translateY = thumbnailPosition.width();
                break;
            case ROTATION_180:
                translateX = thumbnailPosition.width();
                translateY = thumbnailPosition.height();
                break;
        }
        // Adjust translationY with clip hint.
        translateY -= rotationYClipHint;
        mMatrix.postTranslate(translateX, translateY);
    }

    /**
     * Determines whether the thumbnail should be rotated based on aspect ratio differences
     * and split-screen conditions.
     *
     * @param isAspectLargelyDifferent True if the aspect ratio of the task is significantly
     *                                 different from the current display, false otherwise.
     * @return True if the thumbnail should be rotated, false otherwise.
     */
    private boolean shouldRotateThumbnail(boolean isAspectLargelyDifferent) {
        // If aspect ratio is not largely different, always rotate.
        if (!isAspectLargelyDifferent) {
            return true;
        }

        // If no split screen, always not rotate.
        if (mSplitBounds == null) {
            return false;
        }

        // Aspect is different AND split screen is active, check specific split conditions
        boolean isTopLeftSnap = (mSplitBounds.snapPosition == SNAP_TO_2_10_90
                && mSplitPosition == SPLIT_POSITION_TOP_OR_LEFT);
        boolean isBottomRightSnap = (mSplitBounds.snapPosition == SNAP_TO_2_90_10
                && mSplitPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT);

        return isTopLeftSnap || isBottomRightSnap;
    }

    /**
     * Checks if a special translation adjustment is needed for split-screen clipping.
     * This applies when apps are stacked horizontally and specific rotation/snap
     * positions occur, causing part of the thumbnail to be clipped.
     *
     * @param deltaRotate The current rotation delta.
     * @return {@code true} if the adjustment should be applied, {@code false} otherwise.
     */
    private boolean shouldApplySplitScreenClippingAdjustment(int deltaRotate) {
        if (mSplitBounds == null || mSplitBounds.appsStackedVertically) {
            return false;
        }

        boolean isRotated90AndLeftSnapped = (deltaRotate == ROTATION_90
                && mSplitBounds.snapPosition == SNAP_TO_2_10_90
                && mSplitPosition == SPLIT_POSITION_TOP_OR_LEFT);

        boolean isRotated270AndRightSnapped = (deltaRotate == ROTATION_270
                && mSplitBounds.snapPosition == SNAP_TO_2_90_10
                && mSplitPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT);

        return isRotated90AndLeftSnapped || isRotated270AndRightSnapped;
    }

    public void setSplitBounds(SplitBounds splitBounds, int stagePosition) {
        mSplitBounds = splitBounds;
        mSplitPosition = stagePosition;
    }

    /**
     * A factory that returns a new instance of the {@link PreviewPositionHelper}.
     * <p>{@link PreviewPositionHelper} is a stateful helper, and hence when using it in distinct
     * scenarios, prefer fetching an object using this factory</p>
     * <p>Additionally, helpful for injecting mocks in tests</p>
     */
    public static class PreviewPositionHelperFactory {
        /**
         * Returns a new {@link PreviewPositionHelper} for use in a distinct scenario.
         */
        public PreviewPositionHelper create() {
            return new PreviewPositionHelper();
        }
    }
}
