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

package com.android.launcher3.touch;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;
import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.util.FloatProperty;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;

import java.util.List;

public class PortraitPagedViewHandler implements PagedOrientationHandler {

    private final Matrix mTmpMatrix = new Matrix();
    private final RectF mTmpRectF = new RectF();

    @Override
    public <T> T getPrimaryValue(T x, T y) {
        return x;
    }

    @Override
    public <T> T getSecondaryValue(T x, T y) {
        return y;
    }

    @Override
    public int getPrimaryValue(int x, int y) {
        return x;
    }

    @Override
    public int getSecondaryValue(int x, int y) {
        return y;
    }

    @Override
    public float getPrimaryValue(float x, float y) {
        return x;
    }

    @Override
    public float getSecondaryValue(float x, float y) {
        return y;
    }

    @Override
    public boolean isLayoutNaturalToLauncher() {
        return true;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        //no-op
    }

    @Override
    public void fixBoundsForHomeAnimStartRect(RectF outStartRect, DeviceProfile deviceProfile) {
        if (outStartRect.left > deviceProfile.widthPx) {
            outStartRect.offsetTo(0, outStartRect.top);
        } else if (outStartRect.left < -deviceProfile.widthPx) {
            outStartRect.offsetTo(0, outStartRect.top);
        }
    }

    @Override
    public <T> void setPrimary(T target, Int2DAction<T> action, int param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void setPrimary(T target, Float2DAction<T> action, float param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void setSecondary(T target, Float2DAction<T> action, float param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void set(T target, Int2DAction<T> action, int primaryParam,
            int secondaryParam) {
        action.call(target, primaryParam, secondaryParam);
    }

    @Override
    public float getPrimaryDirection(MotionEvent event, int pointerIndex) {
        return event.getX(pointerIndex);
    }

    @Override
    public float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId) {
        return velocityTracker.getXVelocity(pointerId);
    }

    @Override
    public int getMeasuredSize(View view) {
        return view.getMeasuredWidth();
    }

    @Override
    public int getPrimarySize(View view) {
        return view.getWidth();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.width();
    }

    @Override
    public float getStart(RectF rect) {
        return rect.left;
    }

    @Override
    public float getEnd(RectF rect) {
        return rect.right;
    }

    @Override
    public int getClearAllSidePadding(View view, boolean isRtl) {
        return (isRtl ? view.getPaddingRight() : - view.getPaddingLeft()) / 2;
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getHeight();
    }

    @Override
    public FloatProperty<View> getPrimaryViewTranslate() {
        return VIEW_TRANSLATE_X;
    }

    @Override
    public FloatProperty<View> getSecondaryViewTranslate() {
        return VIEW_TRANSLATE_Y;
    }

    @Override
    public int getPrimaryScroll(View view) {
        return view.getScrollX();
    }

    @Override
    public float getPrimaryScale(View view) {
        return view.getScaleX();
    }

    @Override
    public void setMaxScroll(AccessibilityEvent event, int maxScroll) {
        event.setMaxScrollX(maxScroll);
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return !Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 0;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_0;
    }

    @Override
    public void setPrimaryScale(View view, float scale) {
        view.setScaleX(scale);
    }

    @Override
    public void setSecondaryScale(View view, float scale) {
        view.setScaleY(scale);
    }

    @Override
    public int getChildStart(View view) {
        return view.getLeft();
    }

    @Override
    public int getCenterForPage(View view, Rect insets) {
        return (view.getPaddingTop() + view.getMeasuredHeight() + insets.top
            - insets.bottom - view.getPaddingBottom()) / 2;
    }

    @Override
    public int getScrollOffsetStart(View view, Rect insets) {
        return insets.left + view.getPaddingLeft();
    }

    @Override
    public int getScrollOffsetEnd(View view, Rect insets) {
        return view.getWidth() - view.getPaddingRight() - insets.right;
    }

    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition, DeviceProfile deviceProfile) {
        if (deviceProfile.isLandscape && stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView,
            DeviceProfile deviceProfile, float taskInsetMargin) {
        if (deviceProfile.isLandscape) {
            return x + taskInsetMargin
                    + (thumbnailView.getMeasuredWidth() - thumbnailView.getMeasuredHeight()) / 2f;
        } else {
            return x + taskInsetMargin;
        }
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int stagePosition,
            View taskMenuView, float taskInsetMargin) {
        return y + taskInsetMargin;
    }

    @Override
    public int getTaskMenuWidth(View thumbnailView, DeviceProfile deviceProfile,
            @StagePosition int stagePosition) {
        return deviceProfile.isLandscape && !deviceProfile.isTablet
                ? thumbnailView.getMeasuredHeight()
                : thumbnailView.getMeasuredWidth();
    }

    @Override
    public void setTaskOptionsMenuLayoutOrientation(DeviceProfile deviceProfile,
            LinearLayout taskMenuLayout, int dividerSpacing,
            ShapeDrawable dividerDrawable) {
        taskMenuLayout.setOrientation(LinearLayout.VERTICAL);
        dividerDrawable.setIntrinsicHeight(dividerSpacing);
        taskMenuLayout.setDividerDrawable(dividerDrawable);
    }

    @Override
    public void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp,
            LinearLayout viewGroup, DeviceProfile deviceProfile) {
        viewGroup.setOrientation(LinearLayout.HORIZONTAL);
        lp.width = LinearLayout.LayoutParams.MATCH_PARENT;
        lp.height = WRAP_CONTENT;
    }

    @Override
    public Pair<Float, Float> getDwbLayoutTranslations(int taskViewWidth,
            int taskViewHeight, SplitBounds splitBounds, DeviceProfile deviceProfile,
            View[] thumbnailViews, int desiredTaskId, View banner) {
        float translationX = 0;
        float translationY = 0;
        FrameLayout.LayoutParams bannerParams = (FrameLayout.LayoutParams) banner.getLayoutParams();
        banner.setPivotX(0);
        banner.setPivotY(0);
        banner.setRotation(getDegreesRotated());
        if (splitBounds == null) {
            // Single, fullscreen case
            bannerParams.width = MATCH_PARENT;
            bannerParams.gravity = BOTTOM | CENTER_HORIZONTAL;
            return new Pair<>(translationX, translationY);
        }

        bannerParams.gravity = BOTTOM | ((deviceProfile.isLandscape) ? START : CENTER_HORIZONTAL);

        // Set correct width
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            bannerParams.width = thumbnailViews[0].getMeasuredWidth();
        } else {
            bannerParams.width = thumbnailViews[1].getMeasuredWidth();
        }

        // Set translations
        if (deviceProfile.isLandscape) {
            if (desiredTaskId == splitBounds.rightBottomTaskId) {
                float leftTopTaskPercent = splitBounds.appsStackedVertically
                        ? splitBounds.topTaskPercent
                        : splitBounds.leftTaskPercent;
                float dividerThicknessPercent = splitBounds.appsStackedVertically
                        ? splitBounds.dividerHeightPercent
                        : splitBounds.dividerWidthPercent;
                translationX = ((taskViewWidth * leftTopTaskPercent)
                        + (taskViewWidth * dividerThicknessPercent));
            }
        } else {
            if (desiredTaskId == splitBounds.leftTopTaskId) {
                FrameLayout.LayoutParams snapshotParams =
                        (FrameLayout.LayoutParams) thumbnailViews[0]
                                .getLayoutParams();
                float bottomRightTaskPlusDividerPercent = splitBounds.appsStackedVertically
                        ? (1f - splitBounds.topTaskPercent)
                        : (1f - splitBounds.leftTaskPercent);
                translationY = -((taskViewHeight - snapshotParams.topMargin)
                        * bottomRightTaskPlusDividerPercent);
            }
        }
        return new Pair<>(translationX, translationY);
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return VERTICAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return SingleAxisSwipeDetector.DIRECTION_POSITIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return displacement < 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return 1;
    }

    /* -------------------- */

    @Override
    public ChildBounds getChildBounds(View child, int childStart, int pageCenter,
        boolean layoutChild) {
        final int childWidth = child.getMeasuredWidth();
        final int childRight = childStart + childWidth;
        final int childHeight = child.getMeasuredHeight();
        final int childTop = pageCenter - childHeight / 2;
        if (layoutChild) {
            child.layout(childStart, childTop, childRight, childTop + childHeight);
        }
        return new ChildBounds(childWidth, childHeight, childRight, childTop);
    }

    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.heightPx - rect.bottom;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        return Utilities.getSplitPositionOptions(dp);
    }

    @Override
    public void getInitialSplitPlaceholderBounds(int placeholderHeight, int placeholderInset,
            DeviceProfile dp, @StagePosition int stagePosition, Rect out) {
        int screenWidth = dp.widthPx;
        int screenHeight = dp.heightPx;
        boolean pinToRight = stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT;
        int insetSizeAdjustment = getPlaceholderSizeAdjustment(dp, pinToRight);

        out.set(0, 0, screenWidth, placeholderHeight + insetSizeAdjustment);
        if (!dp.isLandscape) {
            // portrait, phone or tablet - spans width of screen, nothing else to do
            out.inset(placeholderInset, 0);

            // Adjust the top to account for content off screen. This will help to animate the view
            // in with rounded corners.
            int totalHeight = (int) (1.0f * screenHeight / 2 * (screenWidth - 2 * placeholderInset)
                    / screenWidth);
            out.top -= (totalHeight - placeholderHeight);
            return;
        }

        // Now we rotate the portrait rect depending on what side we want pinned

        float postRotateScale = (float) screenHeight / screenWidth;
        mTmpMatrix.reset();
        mTmpMatrix.postRotate(pinToRight ? 90 : 270);
        mTmpMatrix.postTranslate(pinToRight ? screenWidth : 0, pinToRight ? 0 : screenWidth);
        // The placeholder height stays constant after rotation, so we don't change width scale
        mTmpMatrix.postScale(1, postRotateScale);

        mTmpRectF.set(out);
        mTmpMatrix.mapRect(mTmpRectF);
        mTmpRectF.inset(0, placeholderInset);
        mTmpRectF.roundOut(out);

        // Adjust the top to account for content off screen. This will help to animate the view in
        // with rounded corners.
        int totalWidth = (int) (1.0f * screenWidth / 2 * (screenHeight - 2 * placeholderInset)
                / screenHeight);
        int width = out.width();
        if (pinToRight) {
            out.right += totalWidth - width;
        } else {
            out.left -= totalWidth - width;
        }
    }

    @Override
    public void updateSplitIconParams(View out, float onScreenRectCenterX,
            float onScreenRectCenterY, float fullscreenScaleX, float fullscreenScaleY,
            int drawableWidth, int drawableHeight, DeviceProfile dp,
            @StagePosition int stagePosition) {
        boolean pinToRight = stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT;
        float insetAdjustment = getPlaceholderSizeAdjustment(dp, pinToRight) / 2f;
        if (!dp.isLandscape) {
            out.setX(onScreenRectCenterX / fullscreenScaleX
                    - 1.0f * drawableWidth / 2);
            out.setY((onScreenRectCenterY + insetAdjustment) / fullscreenScaleY
                    - 1.0f * drawableHeight / 2);
        } else {
            if (pinToRight) {
                out.setX((onScreenRectCenterX - insetAdjustment) / fullscreenScaleX
                        - 1.0f * drawableWidth / 2);
            } else {
                out.setX((onScreenRectCenterX + insetAdjustment) / fullscreenScaleX
                        - 1.0f * drawableWidth / 2);
            }
            out.setY(onScreenRectCenterY / fullscreenScaleY
                    - 1.0f * drawableHeight / 2);
        }
    }

    /**
     * The split placeholder comes with a default inset to buffer the icon from the top of the
     * screen. But if the device already has a large inset (from cutouts etc), use that instead.
     */
    private int getPlaceholderSizeAdjustment(DeviceProfile dp, boolean pinToRight) {
        int insetThickness;
        if (!dp.isLandscape) {
            insetThickness = dp.getInsets().top;
        } else {
            insetThickness = pinToRight ? dp.getInsets().right : dp.getInsets().left;
        }
        return Math.max(insetThickness - dp.splitPlaceholderInset, 0);
    }

    @Override
    public void setSplitInstructionsParams(View out, DeviceProfile dp, int splitInstructionsHeight,
            int splitInstructionsWidth, int threeButtonNavShift) {
        out.setPivotX(0);
        out.setPivotY(splitInstructionsHeight);
        out.setRotation(getDegreesRotated());
        int distanceToEdge;
        if ((DisplayController.getNavigationMode(out.getContext()) == THREE_BUTTONS)
                && (dp.isTwoPanels || dp.isTablet)
                // If taskbar is in overview, overview action has dedicated space above nav buttons
                && !FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get()) {
            // If 3-button nav is active, align the splitInstructionsView with it.
            distanceToEdge = dp.getTaskbarOffsetY()
                    + ((dp.taskbarSize - splitInstructionsHeight) / 2);
        } else {
            // If 3-button nav is not active, set bottom margin according to spec.
            if (dp.isPhone) {
                if (dp.isLandscape) {
                    distanceToEdge = out.getResources().getDimensionPixelSize(
                            R.dimen.split_instructions_bottom_margin_phone_landscape);
                } else {
                    distanceToEdge = out.getResources().getDimensionPixelSize(
                            R.dimen.split_instructions_bottom_margin_phone_portrait);
                }
            } else if (dp.isTwoPanels) {
                if (dp.isLandscape) {
                    distanceToEdge = out.getResources().getDimensionPixelSize(
                            R.dimen.split_instructions_bottom_margin_twopanels_landscape);
                } else {
                    distanceToEdge = out.getResources().getDimensionPixelSize(
                            R.dimen.split_instructions_bottom_margin_twopanels_portrait);
                }
            } else {
                if (dp.isLandscape) {
                    distanceToEdge = out.getResources().getDimensionPixelSize(
                            R.dimen.split_instructions_bottom_margin_tablet_landscape);
                } else {
                    distanceToEdge = out.getResources().getDimensionPixelSize(
                            R.dimen.split_instructions_bottom_margin_tablet_portrait);
                }
            }
        }

        // Center the view in case of unbalanced insets on left or right of screen
        int insetCorrectionX = (dp.getInsets().right - dp.getInsets().left) / 2;
        // Adjust for any insets on the bottom edge
        int insetCorrectionY = dp.getInsets().bottom;
        // Adjust for taskbar in overview
        int taskbarCorrectionY =
                dp.isTaskbarPresent && FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get()
                        ? dp.taskbarSize : 0;
        out.setTranslationX(insetCorrectionX + threeButtonNavShift);
        out.setTranslationY(-distanceToEdge + insetCorrectionY - taskbarCorrectionY);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) out.getLayoutParams();
        lp.gravity = CENTER_HORIZONTAL | BOTTOM;
        out.setLayoutParams(lp);
    }

    @Override
    public void getFinalSplitPlaceholderBounds(int splitDividerSize, DeviceProfile dp,
            @StagePosition int stagePosition, Rect out1, Rect out2) {
        int screenHeight = dp.heightPx;
        int screenWidth = dp.widthPx;
        out1.set(0, 0, screenWidth, screenHeight / 2 - splitDividerSize);
        out2.set(0, screenHeight / 2 + splitDividerSize, screenWidth, screenHeight);
        if (!dp.isLandscape) {
            // Portrait - the window bounds are always top and bottom half
            return;
        }

        // Now we rotate the portrait rect depending on what side we want pinned
        boolean pinToRight = stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT;
        float postRotateScale = (float) screenHeight / screenWidth;

        mTmpMatrix.reset();
        mTmpMatrix.postRotate(pinToRight ? 90 : 270);
        mTmpMatrix.postTranslate(pinToRight ? screenHeight : 0, pinToRight ? 0 : screenWidth);
        mTmpMatrix.postScale(1 / postRotateScale, postRotateScale);

        mTmpRectF.set(out1);
        mTmpMatrix.mapRect(mTmpRectF);
        mTmpRectF.roundOut(out1);

        mTmpRectF.set(out2);
        mTmpMatrix.mapRect(mTmpRectF);
        mTmpRectF.roundOut(out2);
    }

    @Override
    public void setSplitTaskSwipeRect(DeviceProfile dp, Rect outRect,
            SplitBounds splitInfo, int desiredStagePosition) {
        float topLeftTaskPercent = splitInfo.appsStackedVertically
                ? splitInfo.topTaskPercent
                : splitInfo.leftTaskPercent;
        float dividerBarPercent = splitInfo.appsStackedVertically
                ? splitInfo.dividerHeightPercent
                : splitInfo.dividerWidthPercent;

        int deviceHeightWithoutTaskbar = dp.availableHeightPx - dp.taskbarSize;
        float scale = (float) outRect.height() / deviceHeightWithoutTaskbar;
        float topTaskHeight = dp.availableHeightPx * topLeftTaskPercent;
        float scaledTopTaskHeight = topTaskHeight * scale;
        float dividerHeight = dp.availableHeightPx * dividerBarPercent;
        float scaledDividerHeight = dividerHeight * scale;

        if (desiredStagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT) {
            if (splitInfo.appsStackedVertically) {
                outRect.bottom = Math.round(outRect.top + scaledTopTaskHeight);
            } else {
                outRect.right = outRect.left + Math.round(outRect.width() * topLeftTaskPercent);
            }
        } else {
            if (splitInfo.appsStackedVertically) {
                outRect.top += Math.round(scaledTopTaskHeight + scaledDividerHeight);
            } else {
                outRect.left += Math.round(outRect.width()
                        * (topLeftTaskPercent + dividerBarPercent));
            }
        }
    }

    @Override
    public void measureGroupedTaskViewThumbnailBounds(View primarySnapshot, View secondarySnapshot,
            int parentWidth, int parentHeight, SplitBounds splitBoundsConfig,
            DeviceProfile dp, boolean isRtl) {
        int spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx;
        int totalThumbnailHeight = parentHeight - spaceAboveSnapshot;
        int dividerBar = Math.round(splitBoundsConfig.appsStackedVertically
                ? splitBoundsConfig.dividerHeightPercent * dp.availableHeightPx
                : splitBoundsConfig.dividerWidthPercent * parentWidth);
        int primarySnapshotHeight;
        int primarySnapshotWidth;
        int secondarySnapshotHeight;
        int secondarySnapshotWidth;
        float taskPercent = splitBoundsConfig.appsStackedVertically ?
                splitBoundsConfig.topTaskPercent : splitBoundsConfig.leftTaskPercent;
        if (dp.isLandscape) {
            primarySnapshotHeight = totalThumbnailHeight;
            primarySnapshotWidth = Math.round(parentWidth * taskPercent);

            secondarySnapshotHeight = totalThumbnailHeight;
            secondarySnapshotWidth = parentWidth - primarySnapshotWidth - dividerBar;
            int translationX = primarySnapshotWidth + dividerBar;
            if (isRtl) {
                primarySnapshot.setTranslationX(-translationX);
                secondarySnapshot.setTranslationX(0);
            } else {
                secondarySnapshot.setTranslationX(translationX);
                primarySnapshot.setTranslationX(0);
            }
            secondarySnapshot.setTranslationY(spaceAboveSnapshot);
        } else {
            int deviceHeightWithoutTaskbar = dp.availableHeightPx - dp.taskbarSize;
            float scale = (float) totalThumbnailHeight / deviceHeightWithoutTaskbar;
            float topTaskHeight = dp.availableHeightPx * taskPercent;
            float finalDividerHeight = dividerBar * scale;
            float scaledTopTaskHeight = topTaskHeight * scale;
            primarySnapshotWidth = parentWidth;
            primarySnapshotHeight = Math.round(scaledTopTaskHeight);

            secondarySnapshotWidth = parentWidth;
            secondarySnapshotHeight = Math.round(totalThumbnailHeight - primarySnapshotHeight
                    - finalDividerHeight);
            float translationY = primarySnapshotHeight + spaceAboveSnapshot + finalDividerHeight;
            secondarySnapshot.setTranslationY(translationY);

            FrameLayout.LayoutParams primaryParams =
                    (FrameLayout.LayoutParams) primarySnapshot.getLayoutParams();
            FrameLayout.LayoutParams secondaryParams =
                    (FrameLayout.LayoutParams) secondarySnapshot.getLayoutParams();
            secondaryParams.topMargin = 0;
            primaryParams.topMargin = spaceAboveSnapshot;

            // Reset unused translations
            primarySnapshot.setTranslationY(0);
            secondarySnapshot.setTranslationX(0);
            primarySnapshot.setTranslationX(0);
        }
        primarySnapshot.measure(
                View.MeasureSpec.makeMeasureSpec(primarySnapshotWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(primarySnapshotHeight, View.MeasureSpec.EXACTLY));
        secondarySnapshot.measure(
                View.MeasureSpec.makeMeasureSpec(secondarySnapshotWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(secondarySnapshotHeight,
                        View.MeasureSpec.EXACTLY));
    }

    @Override
    public void setTaskIconParams(FrameLayout.LayoutParams iconParams, int taskIconMargin,
            int taskIconHeight, int thumbnailTopMargin, boolean isRtl) {
        iconParams.gravity = TOP | CENTER_HORIZONTAL;
        // Reset margins, since they may have been set on rotation
        iconParams.leftMargin = iconParams.rightMargin = 0;
        iconParams.topMargin = iconParams.bottomMargin = 0;
    }

    @Override
    public void setSplitIconParams(View primaryIconView, View secondaryIconView,
            int taskIconHeight, int primarySnapshotWidth, int primarySnapshotHeight,
            int groupedTaskViewHeight, int groupedTaskViewWidth, boolean isRtl,
            DeviceProfile deviceProfile, SplitBounds splitConfig) {
        FrameLayout.LayoutParams primaryIconParams =
                (FrameLayout.LayoutParams) primaryIconView.getLayoutParams();
        FrameLayout.LayoutParams secondaryIconParams =
                new FrameLayout.LayoutParams(primaryIconParams);

        if (deviceProfile.isLandscape) {
            // We calculate the "midpoint" of the thumbnail area, and place the icons there.
            // This is the place where the thumbnail area splits by default, in a near-50/50 split.
            // It is usually not exactly 50/50, due to insets/screen cutouts.
            int fullscreenInsetThickness = deviceProfile.isSeascape()
                    ? deviceProfile.getInsets().right
                    : deviceProfile.getInsets().left;
            int fullscreenMidpointFromBottom = ((deviceProfile.widthPx
                    - fullscreenInsetThickness) / 2);
            float midpointFromBottomPct = (float) fullscreenMidpointFromBottom
                    / deviceProfile.widthPx;
            float insetPct = (float) fullscreenInsetThickness / deviceProfile.widthPx;
            int spaceAboveSnapshots = 0;
            int overviewThumbnailAreaThickness = groupedTaskViewWidth - spaceAboveSnapshots;
            int bottomToMidpointOffset = (int) (overviewThumbnailAreaThickness
                    * midpointFromBottomPct);
            int insetOffset = (int) (overviewThumbnailAreaThickness * insetPct);

            if (deviceProfile.isSeascape()) {
                primaryIconParams.gravity = TOP | (isRtl ? END : START);
                secondaryIconParams.gravity = TOP | (isRtl ? END : START);
                if (splitConfig.initiatedFromSeascape) {
                    // if the split was initiated from seascape,
                    // the task on the right (secondary) is slightly larger
                    primaryIconView.setTranslationX(bottomToMidpointOffset - taskIconHeight);
                    secondaryIconView.setTranslationX(bottomToMidpointOffset);
                } else {
                    // if not,
                    // the task on the left (primary) is slightly larger
                    primaryIconView.setTranslationX(bottomToMidpointOffset + insetOffset
                            - taskIconHeight);
                    secondaryIconView.setTranslationX(bottomToMidpointOffset + insetOffset);
                }
            } else {
                primaryIconParams.gravity = TOP | (isRtl ? START : END);
                secondaryIconParams.gravity = TOP | (isRtl ? START : END);
                if (!splitConfig.initiatedFromSeascape) {
                    // if the split was initiated from landscape,
                    // the task on the left (primary) is slightly larger
                    primaryIconView.setTranslationX(-bottomToMidpointOffset);
                    secondaryIconView.setTranslationX(-bottomToMidpointOffset + taskIconHeight);
                } else {
                    // if not,
                    // the task on the right (secondary) is slightly larger
                    primaryIconView.setTranslationX(-bottomToMidpointOffset - insetOffset);
                    secondaryIconView.setTranslationX(-bottomToMidpointOffset - insetOffset
                            + taskIconHeight);
                }
            }
        } else {
            primaryIconParams.gravity = TOP | CENTER_HORIZONTAL;
            // shifts icon half a width left (height is used here since icons are square)
            primaryIconView.setTranslationX(-(taskIconHeight / 2f));
            secondaryIconParams.gravity = TOP | CENTER_HORIZONTAL;
            secondaryIconView.setTranslationX(taskIconHeight / 2f);
        }
        primaryIconView.setTranslationY(0);
        secondaryIconView.setTranslationY(0);

        primaryIconView.setLayoutParams(primaryIconParams);
        secondaryIconView.setLayoutParams(secondaryIconParams);
    }

    @Override
    public int getDefaultSplitPosition(DeviceProfile deviceProfile) {
        if (!deviceProfile.isTablet) {
            throw new IllegalStateException("Default position available only for large screens");
        }
        if (deviceProfile.isLandscape) {
            return STAGE_POSITION_BOTTOM_OR_RIGHT;
        } else {
            return STAGE_POSITION_TOP_OR_LEFT;
        }
    }

    @Override
    public Pair<FloatProperty, FloatProperty> getSplitSelectTaskOffset(FloatProperty primary,
            FloatProperty secondary, DeviceProfile deviceProfile) {
        if (deviceProfile.isLandscape) { // or seascape
            return new Pair<>(primary, secondary);
        } else {
            return new Pair<>(secondary, primary);
        }
    }

    @Override
    public float getFloatingTaskOffscreenTranslationTarget(View floatingTask, RectF onScreenRect,
            @StagePosition int stagePosition, DeviceProfile dp) {
        if (dp.isLandscape) {
            float currentTranslationX = floatingTask.getTranslationX();
            return stagePosition == STAGE_POSITION_TOP_OR_LEFT
                    ? currentTranslationX - onScreenRect.width()
                    : currentTranslationX + onScreenRect.width();
        } else {
            float currentTranslationY = floatingTask.getTranslationY();
            return currentTranslationY - onScreenRect.height();
        }
    }

    @Override
    public void setFloatingTaskPrimaryTranslation(View floatingTask, float translation,
            DeviceProfile dp) {
        if (dp.isLandscape) {
            floatingTask.setTranslationX(translation);
        } else {
            floatingTask.setTranslationY(translation);
        }

    }

    @Override
    public Float getFloatingTaskPrimaryTranslation(View floatingTask, DeviceProfile dp) {
        return dp.isLandscape
                ? floatingTask.getTranslationX()
                : floatingTask.getTranslationY();
    }
}
