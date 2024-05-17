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

package com.android.quickstep.orientation;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.Flags.enableOverviewIconMenu;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.util.FloatProperty;
import android.util.Pair;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.touch.DefaultPagedViewHandler;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.quickstep.views.IconAppChipView;

import java.util.ArrayList;
import java.util.List;

public class PortraitPagedViewHandler extends DefaultPagedViewHandler implements
        RecentsPagedOrientationHandler {

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
    public <T> void setSecondary(T target, Float2DAction<T> action, float param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void set(T target, Int2DAction<T> action, int primaryParam,
            int secondaryParam) {
        action.call(target, primaryParam, secondaryParam);
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

    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition, DeviceProfile deviceProfile) {
        if (deviceProfile.isLeftRightSplit && stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView,
            DeviceProfile deviceProfile, float taskInsetMargin, View taskViewIcon) {
        if (deviceProfile.isLandscape) {
            return x + taskInsetMargin
                    + (thumbnailView.getMeasuredWidth() - thumbnailView.getMeasuredHeight()) / 2f;
        } else {
            return x + taskInsetMargin;
        }
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int stagePosition,
            View taskMenuView, float taskInsetMargin, View taskViewIcon) {
        return y + taskInsetMargin;
    }

    @Override
    public int getTaskMenuWidth(View thumbnailView, DeviceProfile deviceProfile,
            @StagePosition int stagePosition) {
        if (enableOverviewIconMenu()) {
            return thumbnailView.getResources().getDimensionPixelSize(
                    R.dimen.task_thumbnail_icon_menu_expanded_width);
        }
        int padding = thumbnailView.getResources()
                .getDimensionPixelSize(R.dimen.task_menu_edge_padding);
        return (deviceProfile.isLandscape && !deviceProfile.isTablet
                ? thumbnailView.getMeasuredHeight()
                : thumbnailView.getMeasuredWidth()) - (2 * padding);
    }

    @Override
    public int getTaskMenuHeight(float taskInsetMargin, DeviceProfile deviceProfile,
            float taskMenuX, float taskMenuY) {
        return (int) (deviceProfile.heightPx - deviceProfile.getInsets().top - taskMenuY
                    - deviceProfile.getOverviewActionsClaimedSpaceBelow());
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
        if (deviceProfile.isLeftRightSplit) {
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
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.heightPx - rect.bottom;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        if (dp.isTablet) {
            return Utilities.getSplitPositionOptions(dp);
        }

        List<SplitPositionOption> options = new ArrayList<>();
        if (dp.isSeascape()) {
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_horizontal, R.string.recent_task_option_split_screen,
                    STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_MAIN));
        } else if (dp.isLeftRightSplit) {
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_horizontal, R.string.recent_task_option_split_screen,
                    STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
        } else {
            // Only add top option
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_vertical, R.string.recent_task_option_split_screen,
                    STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
        }
        return options;
    }

    @Override
    public void getInitialSplitPlaceholderBounds(int placeholderHeight, int placeholderInset,
            DeviceProfile dp, @StagePosition int stagePosition, Rect out) {
        int screenWidth = dp.widthPx;
        int screenHeight = dp.heightPx;
        boolean pinToRight = stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT;
        int insetSizeAdjustment = getPlaceholderSizeAdjustment(dp, pinToRight);

        out.set(0, 0, screenWidth, placeholderHeight + insetSizeAdjustment);
        if (!dp.isLeftRightSplit) {
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
        if (!dp.isLeftRightSplit) {
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
            int splitInstructionsWidth) {
        out.setPivotX(0);
        out.setPivotY(splitInstructionsHeight);
        out.setRotation(getDegreesRotated());
        int distanceToEdge;
        if (dp.isPhone) {
            if (dp.isLandscape) {
                distanceToEdge = out.getResources().getDimensionPixelSize(
                        R.dimen.split_instructions_bottom_margin_phone_landscape);
            } else {
                distanceToEdge = out.getResources().getDimensionPixelSize(
                        R.dimen.split_instructions_bottom_margin_phone_portrait);
            }
        } else {
            distanceToEdge = dp.getOverviewActionsClaimedSpaceBelow();
        }

        // Center the view in case of unbalanced insets on left or right of screen
        int insetCorrectionX = (dp.getInsets().right - dp.getInsets().left) / 2;
        // Adjust for any insets on the bottom edge
        int insetCorrectionY = dp.getInsets().bottom;
        out.setTranslationX(insetCorrectionX);
        out.setTranslationY(-distanceToEdge + insetCorrectionY);
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
        if (!dp.isLeftRightSplit) {
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

        int taskbarHeight = dp.isTransientTaskbar ? 0 : dp.taskbarHeight;
        float scale = (float) outRect.height() / (dp.availableHeightPx - taskbarHeight);
        float topTaskHeight = dp.availableHeightPx * topLeftTaskPercent;
        float scaledTopTaskHeight = topTaskHeight * scale;
        float dividerHeight = dp.availableHeightPx * dividerBarPercent;
        float scaledDividerHeight = dividerHeight * scale;

        if (desiredStagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT) {
            if (dp.isLeftRightSplit) {
                outRect.right = outRect.left + Math.round(outRect.width() * topLeftTaskPercent);
            } else {
                outRect.bottom = Math.round(outRect.top + scaledTopTaskHeight);
            }
        } else {
            if (dp.isLeftRightSplit) {
                outRect.left += Math.round(outRect.width()
                        * (topLeftTaskPercent + dividerBarPercent));
            } else {
                outRect.top += Math.round(scaledTopTaskHeight + scaledDividerHeight);
            }
        }
    }

    @Override
    public void measureGroupedTaskViewThumbnailBounds(View primarySnapshot, View secondarySnapshot,
            int parentWidth, int parentHeight, SplitBounds splitBoundsConfig,
            DeviceProfile dp, boolean isRtl) {
        int spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx;
        int totalThumbnailHeight = parentHeight - spaceAboveSnapshot;
        float dividerScale = splitBoundsConfig.appsStackedVertically
                ? splitBoundsConfig.dividerHeightPercent
                : splitBoundsConfig.dividerWidthPercent;
        Pair<Point, Point> taskViewSizes =
                getGroupedTaskViewSizes(dp, splitBoundsConfig, parentWidth, parentHeight);
        if (dp.isLeftRightSplit) {
            int scaledDividerBar = Math.round(parentWidth * dividerScale);
            if (isRtl) {
                int translationX = taskViewSizes.second.x + scaledDividerBar;
                primarySnapshot.setTranslationX(-translationX);
                secondarySnapshot.setTranslationX(0);
            } else {
                int translationX = taskViewSizes.first.x + scaledDividerBar;
                secondarySnapshot.setTranslationX(translationX);
                primarySnapshot.setTranslationX(0);
            }
            secondarySnapshot.setTranslationY(spaceAboveSnapshot);

            // Reset unused translations
            primarySnapshot.setTranslationY(0);
        } else {
            float finalDividerHeight = Math.round(totalThumbnailHeight * dividerScale);
            float translationY = taskViewSizes.first.y + spaceAboveSnapshot + finalDividerHeight;
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
                View.MeasureSpec.makeMeasureSpec(taskViewSizes.first.x, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(taskViewSizes.first.y, View.MeasureSpec.EXACTLY));
        secondarySnapshot.measure(
                View.MeasureSpec.makeMeasureSpec(taskViewSizes.second.x, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(taskViewSizes.second.y,
                        View.MeasureSpec.EXACTLY));
        primarySnapshot.setScaleX(1);
        secondarySnapshot.setScaleX(1);
        primarySnapshot.setScaleY(1);
        secondarySnapshot.setScaleY(1);
    }

    @Override
    public Pair<Point, Point> getGroupedTaskViewSizes(
            DeviceProfile dp,
            SplitBounds splitBoundsConfig,
            int parentWidth,
            int parentHeight) {
        int spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx;
        int totalThumbnailHeight = parentHeight - spaceAboveSnapshot;
        float dividerScale = splitBoundsConfig.appsStackedVertically
                ? splitBoundsConfig.dividerHeightPercent
                : splitBoundsConfig.dividerWidthPercent;
        float taskPercent = splitBoundsConfig.appsStackedVertically
                ? splitBoundsConfig.topTaskPercent
                : splitBoundsConfig.leftTaskPercent;

        Point firstTaskViewSize = new Point();
        Point secondTaskViewSize = new Point();

        if (dp.isLeftRightSplit) {
            int scaledDividerBar = Math.round(parentWidth * dividerScale);
            firstTaskViewSize.x = Math.round(parentWidth * taskPercent);
            firstTaskViewSize.y = totalThumbnailHeight;

            secondTaskViewSize.x = parentWidth - firstTaskViewSize.x - scaledDividerBar;
            secondTaskViewSize.y = totalThumbnailHeight;
        } else {
            int taskbarHeight = dp.isTransientTaskbar ? 0 : dp.taskbarHeight;
            float scale = (float) totalThumbnailHeight / (dp.availableHeightPx - taskbarHeight);
            float topTaskHeight = dp.availableHeightPx * taskPercent;
            float finalDividerHeight = Math.round(totalThumbnailHeight * dividerScale);
            float scaledTopTaskHeight = topTaskHeight * scale;
            firstTaskViewSize.x = parentWidth;
            firstTaskViewSize.y = Math.round(scaledTopTaskHeight);

            secondTaskViewSize.x = parentWidth;
            secondTaskViewSize.y = Math.round(totalThumbnailHeight - firstTaskViewSize.y
                    - finalDividerHeight);
        }

        return new Pair<>(firstTaskViewSize, secondTaskViewSize);
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
    public void setIconAppChipChildrenParams(FrameLayout.LayoutParams iconParams,
            int chipChildMarginStart) {
        iconParams.setMarginStart(chipChildMarginStart);
        iconParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        iconParams.topMargin = 0;
    }

    @Override
    public void setIconAppChipMenuParams(IconAppChipView iconAppChipView,
            FrameLayout.LayoutParams iconMenuParams, int iconMenuMargin, int thumbnailTopMargin) {
        iconMenuParams.gravity = TOP | START;
        iconMenuParams.setMarginStart(iconMenuMargin);
        iconMenuParams.topMargin = thumbnailTopMargin;
        iconMenuParams.bottomMargin = 0;
        iconMenuParams.setMarginEnd(0);

        iconAppChipView.setPivotX(0);
        iconAppChipView.setPivotY(0);
        iconAppChipView.setSplitTranslationY(0);
        iconAppChipView.setRotation(getDegreesRotated());
    }

    @Override
    public void setSplitIconParams(View primaryIconView, View secondaryIconView,
            int taskIconHeight, int primarySnapshotWidth, int primarySnapshotHeight,
            int groupedTaskViewHeight, int groupedTaskViewWidth, boolean isRtl,
            DeviceProfile deviceProfile, SplitBounds splitConfig) {
        FrameLayout.LayoutParams primaryIconParams =
                (FrameLayout.LayoutParams) primaryIconView.getLayoutParams();
        FrameLayout.LayoutParams secondaryIconParams = enableOverviewIconMenu()
                ? (FrameLayout.LayoutParams) secondaryIconView.getLayoutParams()
                : new FrameLayout.LayoutParams(primaryIconParams);

        if (enableOverviewIconMenu()) {
            IconAppChipView primaryAppChipView = (IconAppChipView) primaryIconView;
            IconAppChipView secondaryAppChipView = (IconAppChipView) secondaryIconView;
            primaryIconParams.gravity = TOP | START;
            secondaryIconParams.gravity = TOP | START;
            secondaryIconParams.topMargin = primaryIconParams.topMargin;
            secondaryIconParams.setMarginStart(primaryIconParams.getMarginStart());
            if (deviceProfile.isLeftRightSplit) {
                if (isRtl) {
                    int secondarySnapshotWidth = groupedTaskViewWidth - primarySnapshotWidth;
                    primaryAppChipView.setSplitTranslationX(-secondarySnapshotWidth);
                } else {
                    secondaryAppChipView.setSplitTranslationX(primarySnapshotWidth);
                }
            } else {
                primaryAppChipView.setSplitTranslationX(0);
                secondaryAppChipView.setSplitTranslationX(0);
                int dividerThickness = Math.min(splitConfig.visualDividerBounds.width(),
                        splitConfig.visualDividerBounds.height());
                secondaryAppChipView.setSplitTranslationY(
                        primarySnapshotHeight + (deviceProfile.isTablet ? 0 : dividerThickness));
            }
        } else if (deviceProfile.isLeftRightSplit) {
            // We calculate the "midpoint" of the thumbnail area, and place the icons there.
            // This is the place where the thumbnail area splits by default, in a near-50/50 split.
            // It is usually not exactly 50/50, due to insets/screen cutouts.
            int fullscreenInsetThickness = deviceProfile.isSeascape()
                    ? deviceProfile.getInsets().right
                    : deviceProfile.getInsets().left;
            int fullscreenMidpointFromBottom = ((deviceProfile.widthPx
                    - fullscreenInsetThickness) / 2);
            float midpointFromEndPct = (float) fullscreenMidpointFromBottom
                    / deviceProfile.widthPx;
            float insetPct = (float) fullscreenInsetThickness / deviceProfile.widthPx;
            int spaceAboveSnapshots = 0;
            int overviewThumbnailAreaThickness = groupedTaskViewWidth - spaceAboveSnapshots;
            int bottomToMidpointOffset = (int) (overviewThumbnailAreaThickness
                    * midpointFromEndPct);
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
        if (!enableOverviewIconMenu()) {
            primaryIconView.setTranslationY(0);
            secondaryIconView.setTranslationY(0);
        }

        primaryIconView.setLayoutParams(primaryIconParams);
        secondaryIconView.setLayoutParams(secondaryIconParams);
    }

    @Override
    public int getDefaultSplitPosition(DeviceProfile deviceProfile) {
        if (!deviceProfile.isTablet) {
            throw new IllegalStateException("Default position available only for large screens");
        }
        if (deviceProfile.isLeftRightSplit) {
            return STAGE_POSITION_BOTTOM_OR_RIGHT;
        } else {
            return STAGE_POSITION_TOP_OR_LEFT;
        }
    }

    @Override
    public Pair<FloatProperty, FloatProperty> getSplitSelectTaskOffset(FloatProperty primary,
            FloatProperty secondary, DeviceProfile deviceProfile) {
        if (deviceProfile.isLeftRightSplit) { // or seascape
            return new Pair<>(primary, secondary);
        } else {
            return new Pair<>(secondary, primary);
        }
    }

    @Override
    public float getFloatingTaskOffscreenTranslationTarget(View floatingTask, RectF onScreenRect,
            @StagePosition int stagePosition, DeviceProfile dp) {
        if (dp.isLeftRightSplit) {
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
        if (dp.isLeftRightSplit) {
            floatingTask.setTranslationX(translation);
        } else {
            floatingTask.setTranslationY(translation);
        }

    }

    @Override
    public float getFloatingTaskPrimaryTranslation(View floatingTask, DeviceProfile dp) {
        return dp.isLeftRightSplit
                ? floatingTask.getTranslationX()
                : floatingTask.getTranslationY();
    }

    @NonNull
    @Override
    public LauncherAtom.TaskSwitcherContainer.OrientationHandler getHandlerTypeForLogging() {
        return LauncherAtom.TaskSwitcherContainer.OrientationHandler.PORTRAIT;
    }
}
