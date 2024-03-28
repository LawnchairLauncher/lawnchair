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
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.View.LAYOUT_DIRECTION_RTL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.Flags.enableOverviewIconMenu;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.util.FloatProperty;
import android.util.Pair;
import android.view.Gravity;
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
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Collections;
import java.util.List;

public class LandscapePagedViewHandler implements PagedOrientationHandler {

    @Override
    public <T> T getPrimaryValue(T x, T y) {
        return y;
    }

    @Override
    public <T> T getSecondaryValue(T x, T y) {
        return x;
    }

    @Override
    public int getPrimaryValue(int x, int y) {
        return y;
    }

    @Override
    public int getSecondaryValue(int x, int y) {
        return x;
    }

    @Override
    public float getPrimaryValue(float x, float y) {
        return y;
    }

    @Override
    public float getSecondaryValue(float x, float y) {
        return x;
    }

    @Override
    public boolean isLayoutNaturalToLauncher() {
        return false;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        float oldX = velocity.x;
        float oldY = velocity.y;
        velocity.set(-oldY, oldX);
    }

    @Override
    public void fixBoundsForHomeAnimStartRect(RectF outStartRect, DeviceProfile deviceProfile) {
        // We don't need to check the "top" value here because the startRect is in the orientation
        // of the app, not of the fixed portrait launcher.
        if (outStartRect.left > deviceProfile.heightPx) {
            outStartRect.offsetTo(0, outStartRect.top);
        } else if (outStartRect.left < -deviceProfile.heightPx) {
            outStartRect.offsetTo(0, outStartRect.top);
        }
    }

    @Override
    public <T> void setPrimary(T target, Int2DAction<T> action, int param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void setPrimary(T target, Float2DAction<T> action, float param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void setSecondary(T target, Float2DAction<T> action, float param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void set(T target, Int2DAction<T> action, int primaryParam,
            int secondaryParam) {
        action.call(target, secondaryParam, primaryParam);
    }

    @Override
    public float getPrimaryDirection(MotionEvent event, int pointerIndex) {
        return event.getY(pointerIndex);
    }

    @Override
    public float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId) {
        return velocityTracker.getYVelocity(pointerId);
    }

    @Override
    public int getMeasuredSize(View view) {
        return view.getMeasuredHeight();
    }

    @Override
    public int getPrimarySize(View view) {
        return view.getHeight();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.height();
    }

    @Override
    public float getStart(RectF rect) {
        return rect.top;
    }

    @Override
    public float getEnd(RectF rect) {
        return rect.bottom;
    }

    @Override
    public int getClearAllSidePadding(View view, boolean isRtl) {
        return (isRtl ? view.getPaddingBottom() : - view.getPaddingTop()) / 2;
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getWidth();
    }

    @Override
    public FloatProperty<View> getPrimaryViewTranslate() {
        return VIEW_TRANSLATE_Y;
    }

    @Override
    public FloatProperty<View> getSecondaryViewTranslate() {
        return VIEW_TRANSLATE_X;
    }

    @Override
    public int getPrimaryScroll(View view) {
        return view.getScrollY();
    }

    @Override
    public float getPrimaryScale(View view) {
        return view.getScaleY();
    }

    @Override
    public void setMaxScroll(AccessibilityEvent event, int maxScroll) {
        event.setMaxScrollY(maxScroll);
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return !Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 90;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_90;
    }

    @Override
    public void setPrimaryScale(View view, float scale) {
        view.setScaleY(scale);
    }

    @Override
    public void setSecondaryScale(View view, float scale) {
        view.setScaleX(scale);
    }

    @Override
    public int getChildStart(View view) {
        return view.getTop();
    }

    @Override
    public int getCenterForPage(View view, Rect insets) {
        return (view.getPaddingLeft() + view.getMeasuredWidth() + insets.left
            - insets.right - view.getPaddingRight()) / 2;
    }

    @Override
    public int getScrollOffsetStart(View view, Rect insets) {
        return insets.top + view.getPaddingTop();
    }

    @Override
    public int getScrollOffsetEnd(View view, Rect insets) {
        return view.getHeight() - view.getPaddingBottom() - insets.bottom;
    }

    public int getSecondaryTranslationDirectionFactor() {
        return 1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition, DeviceProfile deviceProfile) {
        if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView,
            DeviceProfile deviceProfile, float taskInsetMargin, View taskViewIcon) {
        if (enableOverviewIconMenu()) {
            return x + (taskInsetMargin / 2f);
        }
        return thumbnailView.getMeasuredWidth() + x - taskInsetMargin;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int stagePosition,
            View taskMenuView, float taskInsetMargin, View taskViewIcon) {
        if (enableOverviewIconMenu()) {
            return y - (thumbnailView.getLayoutDirection() == LAYOUT_DIRECTION_RTL
                    ? taskMenuView.getMeasuredHeight() * 2 - (taskInsetMargin / 2f)
                    : taskMenuView.getMeasuredHeight());

        }
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) taskMenuView.getLayoutParams();
        int taskMenuWidth = lp.width;
        if (stagePosition == STAGE_POSITION_UNDEFINED) {
            return y + taskInsetMargin
                    + (thumbnailView.getMeasuredHeight() - taskMenuWidth) / 2f;
        } else {
            return y + taskInsetMargin;
        }
    }

    @Override
    public int getTaskMenuWidth(View thumbnailView, DeviceProfile deviceProfile,
            @StagePosition int stagePosition) {
        if (enableOverviewIconMenu()) {
            return thumbnailView.getResources().getDimensionPixelSize(
                    R.dimen.task_thumbnail_icon_menu_max_width);
        }
        if (stagePosition == SplitConfigurationOptions.STAGE_POSITION_UNDEFINED) {
            return thumbnailView.getMeasuredWidth();
        } else {
            return thumbnailView.getMeasuredHeight();
        }
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
        // Phone fake landscape
        viewGroup.setOrientation(LinearLayout.HORIZONTAL);
        lp.width = MATCH_PARENT;
        lp.height = WRAP_CONTENT;
    }

    @Override
    public Pair<Float, Float> getDwbLayoutTranslations(int taskViewWidth,
            int taskViewHeight, SplitBounds splitBounds, DeviceProfile deviceProfile,
            View[] thumbnailViews, int desiredTaskId, View banner) {
        boolean isRtl = banner.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float translationX = 0;
        float translationY = 0;
        FrameLayout.LayoutParams bannerParams = (FrameLayout.LayoutParams) banner.getLayoutParams();
        banner.setPivotX(0);
        banner.setPivotY(0);
        banner.setRotation(getDegreesRotated());
        translationX = banner.getHeight();
        FrameLayout.LayoutParams snapshotParams =
                (FrameLayout.LayoutParams) thumbnailViews[0]
                        .getLayoutParams();
        bannerParams.gravity = TOP | (isRtl ? END : START);
        if (splitBounds == null) {
            // Single, fullscreen case
            bannerParams.width = taskViewHeight - snapshotParams.topMargin;
            return new Pair<>(translationX, Integer.valueOf(snapshotParams.topMargin).floatValue());
        }

        // Set correct width
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            bannerParams.width = thumbnailViews[0].getMeasuredHeight();
        } else {
            bannerParams.width = thumbnailViews[1].getMeasuredHeight();
        }

        // Set translations
        if (desiredTaskId == splitBounds.rightBottomTaskId) {
            float topLeftTaskPlusDividerPercent = splitBounds.appsStackedVertically
                    ? (splitBounds.topTaskPercent + splitBounds.dividerHeightPercent)
                    : (splitBounds.leftTaskPercent + splitBounds.dividerWidthPercent);
            translationY = snapshotParams.topMargin
                    + ((taskViewHeight - snapshotParams.topMargin) * topLeftTaskPlusDividerPercent);
        }
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            translationY = snapshotParams.topMargin;
        }
        return new Pair<>(translationX, translationY);
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return HORIZONTAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        return isRtl ? SingleAxisSwipeDetector.DIRECTION_NEGATIVE
                : SingleAxisSwipeDetector.DIRECTION_POSITIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        return isRtl ? displacement < 0 : displacement > 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        return isRtl ? 1 : -1;
    }

    /* -------------------- */

    @Override
    public ChildBounds getChildBounds(View child, int childStart, int pageCenter,
        boolean layoutChild) {
        final int childHeight = child.getMeasuredHeight();
        final int childBottom = childStart + childHeight;
        final int childWidth = child.getMeasuredWidth();
        final int childLeft = pageCenter - childWidth/ 2;
        if (layoutChild) {
            child.layout(childLeft, childStart, childLeft + childWidth, childBottom);
        }
        return new ChildBounds(childHeight, childWidth, childBottom, childLeft);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return rect.left;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        // Add "left" side of phone which is actually the top
        return Collections.singletonList(new SplitPositionOption(
                R.drawable.ic_split_horizontal, R.string.recent_task_option_split_screen,
                STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
    }

    @Override
    public void getInitialSplitPlaceholderBounds(int placeholderHeight, int placeholderInset,
            DeviceProfile dp, @StagePosition int stagePosition, Rect out) {
        // In fake land/seascape, the placeholder always needs to go to the "top" of the device,
        // which is the same bounds as 0 rotation.
        int width = dp.widthPx;
        int insetSizeAdjustment = getPlaceholderSizeAdjustment(dp);
        out.set(0, 0, width, placeholderHeight + insetSizeAdjustment);
        out.inset(placeholderInset, 0);

        // Adjust the top to account for content off screen. This will help to animate the view in
        // with rounded corners.
        int screenWidth = dp.widthPx;
        int screenHeight = dp.heightPx;
        int totalHeight = (int) (1.0f * screenHeight / 2 * (screenWidth - 2 * placeholderInset)
                / screenWidth);
        out.top -= (totalHeight - placeholderHeight);
    }

    @Override
    public void updateSplitIconParams(View out, float onScreenRectCenterX,
            float onScreenRectCenterY, float fullscreenScaleX, float fullscreenScaleY,
            int drawableWidth, int drawableHeight, DeviceProfile dp,
            @StagePosition int stagePosition) {
        float insetAdjustment = getPlaceholderSizeAdjustment(dp) / 2f;
        out.setX(onScreenRectCenterX / fullscreenScaleX
                - 1.0f * drawableWidth / 2);
        out.setY((onScreenRectCenterY + insetAdjustment) / fullscreenScaleY
                - 1.0f * drawableHeight / 2);
    }

    /**
     * The split placeholder comes with a default inset to buffer the icon from the top of the
     * screen. But if the device already has a large inset (from cutouts etc), use that instead.
     */
    private int getPlaceholderSizeAdjustment(DeviceProfile dp) {
        return Math.max(dp.getInsets().top - dp.splitPlaceholderInset, 0);
    }

    @Override
    public void setSplitInstructionsParams(View out, DeviceProfile dp, int splitInstructionsHeight,
            int splitInstructionsWidth) {
        out.setPivotX(0);
        out.setPivotY(splitInstructionsHeight);
        out.setRotation(getDegreesRotated());
        int distanceToEdge = out.getResources().getDimensionPixelSize(
                R.dimen.split_instructions_bottom_margin_phone_landscape);
        // Adjust for any insets on the left edge
        int insetCorrectionX = dp.getInsets().left;
        // Center the view in case of unbalanced insets on top or bottom of screen
        int insetCorrectionY = (dp.getInsets().bottom - dp.getInsets().top) / 2;
        out.setTranslationX(distanceToEdge - insetCorrectionX);
        out.setTranslationY(((-splitInstructionsHeight - splitInstructionsWidth) / 2f)
                + insetCorrectionY);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) out.getLayoutParams();
        // Setting gravity to LEFT instead of the lint-recommended START because we always want this
        // view to be screen-left when phone is in landscape, regardless of the RtL setting.
        lp.gravity = LEFT | CENTER_VERTICAL;
        out.setLayoutParams(lp);
    }

    @Override
    public void getFinalSplitPlaceholderBounds(int splitDividerSize, DeviceProfile dp,
            @StagePosition int stagePosition, Rect out1, Rect out2) {
        // In fake land/seascape, the window bounds are always top and bottom half
        int screenHeight = dp.heightPx;
        int screenWidth = dp.widthPx;
        out1.set(0, 0, screenWidth, screenHeight / 2  - splitDividerSize);
        out2.set(0, screenHeight / 2  + splitDividerSize, screenWidth, screenHeight);
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

        if (desiredStagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT) {
            outRect.bottom = outRect.top + (int) (outRect.height() * topLeftTaskPercent);
        } else {
            outRect.top += (int) (outRect.height() * (topLeftTaskPercent + dividerBarPercent));
        }
    }

    @Override
    public void measureGroupedTaskViewThumbnailBounds(View primarySnapshot, View secondarySnapshot,
            int parentWidth, int parentHeight, SplitBounds splitBoundsConfig, DeviceProfile dp,
            boolean isRtl) {
        FrameLayout.LayoutParams primaryParams =
                (FrameLayout.LayoutParams) primarySnapshot.getLayoutParams();
        FrameLayout.LayoutParams secondaryParams =
                (FrameLayout.LayoutParams) secondarySnapshot.getLayoutParams();

        // Swap the margins that are set in TaskView#setRecentsOrientedState()
        secondaryParams.topMargin = dp.overviewTaskThumbnailTopMarginPx;
        primaryParams.topMargin = 0;

        // Measure and layout the thumbnails bottom up, since the primary is on the visual left
        // (portrait bottom) and secondary is on the right (portrait top)
        int spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx;
        int totalThumbnailHeight = parentHeight - spaceAboveSnapshot;
        int dividerBar = Math.round(totalThumbnailHeight * (splitBoundsConfig.appsStackedVertically
                ? splitBoundsConfig.dividerHeightPercent
                : splitBoundsConfig.dividerWidthPercent));
        int primarySnapshotHeight;
        int primarySnapshotWidth;
        int secondarySnapshotHeight;
        int secondarySnapshotWidth;

        float taskPercent = splitBoundsConfig.appsStackedVertically ?
                splitBoundsConfig.topTaskPercent : splitBoundsConfig.leftTaskPercent;
        primarySnapshotWidth = parentWidth;
        primarySnapshotHeight = (int) (totalThumbnailHeight * (taskPercent));

        secondarySnapshotWidth = parentWidth;
        secondarySnapshotHeight = totalThumbnailHeight - primarySnapshotHeight - dividerBar;
        secondarySnapshot.setTranslationY(0);
        primarySnapshot.setTranslationY(secondarySnapshotHeight + spaceAboveSnapshot + dividerBar);
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
        if (enableOverviewIconMenu()) {
            iconParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            iconParams.topMargin = 0;
            return;
        }
        iconParams.gravity = (isRtl ? START : END) | CENTER_VERTICAL;
        iconParams.rightMargin = -taskIconHeight - taskIconMargin / 2;
        iconParams.leftMargin = 0;
        iconParams.topMargin = thumbnailTopMargin / 2;
        iconParams.bottomMargin = 0;
    }

    @Override
    public void setIconAppChipMenuParams(View iconAppChipMenuView,
            FrameLayout.LayoutParams iconMenuParams, int iconMenuMargin, int thumbnailTopMargin) {
        boolean isRtl = iconAppChipMenuView.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        iconMenuParams.gravity = (isRtl ? START : END) | (isRtl ? BOTTOM : TOP);
        iconMenuParams.setMarginStart(isRtl ? iconMenuMargin : 0);
        iconMenuParams.topMargin = iconMenuMargin;
        iconMenuParams.bottomMargin = isRtl ? iconMenuMargin : 0;
        iconMenuParams.setMarginEnd(iconMenuMargin);

        iconAppChipMenuView.setPivotX(isRtl ? iconMenuParams.width - (iconMenuParams.height / 2f)
                : iconMenuParams.width / 2f);
        iconAppChipMenuView.setPivotY(
                isRtl ? (iconMenuParams.height / 2f) : iconMenuParams.width / 2f);
        iconAppChipMenuView.setTranslationY(0);
        iconAppChipMenuView.setRotation(getDegreesRotated());
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

        // We calculate the "midpoint" of the thumbnail area, and place the icons there.
        // This is the place where the thumbnail area splits by default, in a near-50/50 split.
        // It is usually not exactly 50/50, due to insets/screen cutouts.
        int fullscreenInsetThickness = deviceProfile.getInsets().top
                - deviceProfile.getInsets().bottom;
        int fullscreenMidpointFromBottom = ((deviceProfile.heightPx - fullscreenInsetThickness)
                / 2);
        float midpointFromBottomPct = (float) fullscreenMidpointFromBottom / deviceProfile.heightPx;
        float insetPct = (float) fullscreenInsetThickness / deviceProfile.heightPx;
        int spaceAboveSnapshots = deviceProfile.overviewTaskThumbnailTopMarginPx;
        int overviewThumbnailAreaThickness = groupedTaskViewHeight - spaceAboveSnapshots;
        int bottomToMidpointOffset = (int) (overviewThumbnailAreaThickness * midpointFromBottomPct);
        int insetOffset = (int) (overviewThumbnailAreaThickness * insetPct);

        if (enableOverviewIconMenu()) {
            primaryIconParams.gravity = isRtl ? BOTTOM | START : TOP | END;
            secondaryIconParams.gravity = isRtl ? BOTTOM | START : TOP | END;
        } else {
            primaryIconParams.gravity = BOTTOM | (isRtl ? START : END);
            secondaryIconParams.gravity = BOTTOM | (isRtl ? START : END);
        }
        primaryIconView.setTranslationX(0);
        secondaryIconView.setTranslationX(0);
        if (enableOverviewIconMenu()) {
            if (primaryIconView.getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                secondaryIconView.setTranslationY(-primarySnapshotHeight);
                primaryIconView.setTranslationY(0);
            } else {
                int secondarySnapshotHeight = groupedTaskViewHeight - primarySnapshotHeight;
                primaryIconView.setTranslationY(secondarySnapshotHeight);
            }
        } else if (splitConfig.initiatedFromSeascape) {
            // if the split was initiated from seascape,
            // the task on the right (secondary) is slightly larger
            primaryIconView.setTranslationY(-bottomToMidpointOffset - insetOffset);
            secondaryIconView.setTranslationY(-bottomToMidpointOffset - insetOffset
                    + taskIconHeight);
        } else {
            // if not,
            // the task on the left (primary) is slightly larger
            primaryIconView.setTranslationY(-bottomToMidpointOffset);
            secondaryIconView.setTranslationY(-bottomToMidpointOffset + taskIconHeight);
        }

        primaryIconView.setLayoutParams(primaryIconParams);
        secondaryIconView.setLayoutParams(secondaryIconParams);
    }

    @Override
    public int getDefaultSplitPosition(DeviceProfile deviceProfile) {
        throw new IllegalStateException("Default position not available in fake landscape");
    }

    @Override
    public Pair<FloatProperty, FloatProperty> getSplitSelectTaskOffset(FloatProperty primary,
            FloatProperty secondary, DeviceProfile deviceProfile) {
        return new Pair<>(primary, secondary);
    }

    @Override
    public float getFloatingTaskOffscreenTranslationTarget(View floatingTask, RectF onScreenRect,
            @StagePosition int stagePosition, DeviceProfile dp) {
        float currentTranslationY = floatingTask.getTranslationY();
        return currentTranslationY - onScreenRect.height();
    }

    @Override
    public void setFloatingTaskPrimaryTranslation(View floatingTask, float translation,
            DeviceProfile dp) {
        floatingTask.setTranslationY(translation);
    }

    @Override
    public Float getFloatingTaskPrimaryTranslation(View floatingTask, DeviceProfile dp) {
        return floatingTask.getTranslationY();
    }
}
