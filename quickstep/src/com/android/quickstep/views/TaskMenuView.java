/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep.views;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.launcher3.Flags.enableOverviewIconMenu;
import static com.android.quickstep.views.TaskThumbnailView.DIM_ALPHA;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer;

/**
 * Contains options for a recent task when long-pressing its icon.
 */
public class TaskMenuView extends AbstractFloatingView {

    private static final Rect sTempRect = new Rect();

    private static final int REVEAL_OPEN_DURATION = enableOverviewIconMenu() ? 417 : 150;
    private static final int REVEAL_CLOSE_DURATION = enableOverviewIconMenu() ? 333 : 100;

    private BaseDraggingActivity mActivity;
    private TextView mTaskName;
    @Nullable
    private AnimatorSet mOpenCloseAnimator;
    @Nullable private Runnable mOnClosingStartCallback;
    private TaskView mTaskView;
    private TaskIdAttributeContainer mTaskContainer;
    private LinearLayout mOptionLayout;
    private float mMenuTranslationYBeforeOpen;
    private float mIconViewTranslationYBeforeOpen;

    public TaskMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mActivity = BaseDraggingActivity.fromContext(context);
        setClipToOutline(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTaskName = findViewById(R.id.task_name);
        mOptionLayout = findViewById(R.id.menu_option_layout);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = mActivity.getDragLayer();
            if (!dl.isEventOverView(this, ev)) {
                // TODO: log this once we have a new container type for it?
                close(true);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASK_MENU) != 0;
    }

    @Override
    public ViewOutlineProvider getOutlineProvider() {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        TaskCornerRadius.get(view.getContext()));
            }
        };
    }

    public void onRotationChanged() {
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            mOpenCloseAnimator.end();
        }
        if (mIsOpen) {
            mOptionLayout.removeAllViews();
            if (!populateAndLayoutMenu()) {
                close(false);
            }
        }
    }

    public static boolean showForTask(TaskIdAttributeContainer taskContainer,
            @Nullable Runnable onClosingStartCallback) {
        BaseDraggingActivity activity = BaseDraggingActivity.fromContext(
                taskContainer.getTaskView().getContext());
        final TaskMenuView taskMenuView = (TaskMenuView) activity.getLayoutInflater().inflate(
                        R.layout.task_menu, activity.getDragLayer(), false);
        taskMenuView.setOnClosingStartCallback(onClosingStartCallback);
        return taskMenuView.populateAndShowForTask(taskContainer);
    }

    public static boolean showForTask(TaskIdAttributeContainer taskContainer) {
        return showForTask(taskContainer, null);
    }

    private boolean populateAndShowForTask(TaskIdAttributeContainer taskContainer) {
        if (isAttachedToWindow()) {
            return false;
        }
        mActivity.getDragLayer().addView(this);
        mTaskView = taskContainer.getTaskView();
        mTaskContainer = taskContainer;
        if (!populateAndLayoutMenu()) {
            return false;
        }
        post(this::animateOpen);
        return true;
    }

    /** @return true if successfully able to populate task view menu, false otherwise */
    private boolean populateAndLayoutMenu() {
        if (mTaskContainer.getTask().icon == null) {
            // Icon may not be loaded
            return false;
        }
        addMenuOptions(mTaskContainer);
        orientAroundTaskView(mTaskContainer);
        return true;
    }

    private void addMenuOptions(TaskIdAttributeContainer taskContainer) {
        if (enableOverviewIconMenu()) {
            removeView(mTaskName);
        } else {
            mTaskName.setText(TaskUtils.getTitle(getContext(), taskContainer.getTask()));
            mTaskName.setOnClickListener(v -> close(true));
        }
        TaskOverlayFactory.getEnabledShortcuts(mTaskView, taskContainer)
                .forEach(this::addMenuOption);
    }

    private void addMenuOption(SystemShortcut menuOption) {
        LinearLayout menuOptionView = (LinearLayout) mActivity.getLayoutInflater().inflate(
                R.layout.task_view_menu_option, this, false);
        if (enableOverviewIconMenu()) {
            ((GradientDrawable) menuOptionView.getBackground()).setCornerRadius(0);
        }
        menuOption.setIconAndLabelFor(
                menuOptionView.findViewById(R.id.icon), menuOptionView.findViewById(R.id.text));
        LayoutParams lp = (LayoutParams) menuOptionView.getLayoutParams();
        mTaskView.getPagedOrientationHandler().setLayoutParamsForTaskMenuOptionItem(lp,
                menuOptionView, mActivity.getDeviceProfile());
        // Set an onClick listener on each menu option. The onClick method is responsible for
        // ending LiveTile mode on the thumbnail if needed.
        menuOptionView.setOnClickListener(menuOption::onClick);
        mOptionLayout.addView(menuOptionView);
    }

    private void orientAroundTaskView(TaskIdAttributeContainer taskContainer) {
        RecentsView recentsView = mActivity.getOverviewPanel();
        PagedOrientationHandler orientationHandler = recentsView.getPagedOrientationHandler();
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

        // Get Position
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        mActivity.getDragLayer().getDescendantRectRelativeToSelf(
                enableOverviewIconMenu() ? taskContainer.getIconView().asView()
                        : taskContainer.getThumbnailView(), sTempRect);
        Rect insets = mActivity.getDragLayer().getInsets();
        BaseDragLayer.LayoutParams params = (BaseDragLayer.LayoutParams) getLayoutParams();
        int padding = getResources()
                .getDimensionPixelSize(R.dimen.task_menu_vertical_padding);
        params.width = orientationHandler
                .getTaskMenuWidth(taskContainer.getThumbnailView(),
                        deviceProfile, taskContainer.getStagePosition()) - (2 * padding);
        // Gravity set to Left instead of Start as sTempRect.left measures Left distance not Start
        params.gravity = Gravity.LEFT;
        setLayoutParams(params);
        setScaleX(mTaskView.getScaleX());
        setScaleY(mTaskView.getScaleY());

        // Set divider spacing
        ShapeDrawable divider = new ShapeDrawable(new RectShape());
        divider.getPaint().setColor(getResources().getColor(android.R.color.transparent));
        int dividerSpacing = (int) getResources().getDimension(R.dimen.task_menu_spacing);
        mOptionLayout.setShowDividers(
                enableOverviewIconMenu() ? SHOW_DIVIDER_NONE : SHOW_DIVIDER_MIDDLE);

        orientationHandler.setTaskOptionsMenuLayoutOrientation(
                deviceProfile, mOptionLayout, dividerSpacing, divider);
        float thumbnailAlignedX = sTempRect.left - insets.left + (enableOverviewIconMenu()
                ? -getResources().getDimensionPixelSize(
                R.dimen.task_thumbnail_icon_menu_touch_max_margin) : 0);
        float thumbnailAlignedY = sTempRect.top - insets.top + (enableOverviewIconMenu()
                ? getResources().getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_max_height)
                - 2 * dividerSpacing : 0);
        // Changing pivot to make computations easier
        // NOTE: Changing the pivots means the rotated view gets rotated about the new pivots set,
        // which would render the X and Y position set here incorrect
        setPivotX(0);
        setPivotY(0);
        setRotation(orientationHandler.getDegreesRotated());

        // Margin that insets the menuView inside the taskView
        float taskInsetMargin =
                enableOverviewIconMenu() ? getResources().getDimension(
                        R.dimen.task_thumbnail_icon_menu_margin) : getResources().getDimension(
                        R.dimen.task_card_margin);
        setTranslationX(orientationHandler.getTaskMenuX(thumbnailAlignedX,
                mTaskContainer.getThumbnailView(), deviceProfile, taskInsetMargin,
                mTaskContainer.getIconView().asView()));
        setTranslationY(orientationHandler.getTaskMenuY(
                thumbnailAlignedY, mTaskContainer.getThumbnailView(),
                mTaskContainer.getStagePosition(), this, taskInsetMargin,
                mTaskContainer.getIconView().asView()));
    }

    private void animateOpen() {
        mMenuTranslationYBeforeOpen = getTranslationY();
        mIconViewTranslationYBeforeOpen = mTaskContainer.getIconView().asView().getTranslationY();
        animateOpenOrClosed(false);
        mIsOpen = true;
    }

    private void animateClose() {
        animateOpenOrClosed(true);
    }

    private void animateOpenOrClosed(boolean closing) {
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            mOpenCloseAnimator.end();
        }
        mOpenCloseAnimator = new AnimatorSet();

        final Animator revealAnimator = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, closing);
        revealAnimator.setInterpolator(enableOverviewIconMenu() ? Interpolators.EMPHASIZED
                : Interpolators.DECELERATE);

        if (enableOverviewIconMenu()
                && ((RecentsView) mActivity.getOverviewPanel()).isOnGridBottomRow(mTaskView)) {
            float taskBottom = mTaskView.getHeight() + mTaskView.getPersistentTranslationY();
            float menuBottom = getHeight() + mMenuTranslationYBeforeOpen;
            float additionalTranslationY = Math.max(menuBottom - taskBottom, 0);

            ObjectAnimator translationYAnim = ObjectAnimator.ofFloat(this, TRANSLATION_Y,
                    closing ? mMenuTranslationYBeforeOpen
                            : mMenuTranslationYBeforeOpen - additionalTranslationY);
            translationYAnim.setInterpolator(EMPHASIZED);

            ObjectAnimator menuTranslationYAnim = ObjectAnimator.ofFloat(
                    mTaskContainer.getIconView().asView(), TRANSLATION_Y,
                    closing ? mIconViewTranslationYBeforeOpen
                            : mIconViewTranslationYBeforeOpen - additionalTranslationY);
            menuTranslationYAnim.setInterpolator(EMPHASIZED);

            mOpenCloseAnimator.playTogether(translationYAnim, menuTranslationYAnim);
        }

        mOpenCloseAnimator.playTogether(revealAnimator,
                ObjectAnimator.ofFloat(
                        mTaskContainer.getThumbnailView(), DIM_ALPHA,
                        closing ? 0 : TaskView.MAX_PAGE_SCRIM_ALPHA),
                ObjectAnimator.ofFloat(this, ALPHA, closing ? 0 : 1));
        mOpenCloseAnimator.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
                if (closing && mOnClosingStartCallback != null) {
                    mOnClosingStartCallback.run();
                }
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                if (closing) {
                    closeComplete();
                }
            }
        });
        mOpenCloseAnimator.setDuration(closing ? REVEAL_CLOSE_DURATION: REVEAL_OPEN_DURATION);
        mOpenCloseAnimator.start();
    }

    private void closeComplete() {
        mIsOpen = false;
        mActivity.getDragLayer().removeView(this);
    }

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        float radius = TaskCornerRadius.get(mContext);
        Rect fromRect = new Rect(
                enableOverviewIconMenu() && isLayoutRtl() ? getWidth() : 0,
                0,
                enableOverviewIconMenu() && !isLayoutRtl() ? 0 : getWidth(),
                0);
        Rect toRect = new Rect(0, 0, getWidth(), getHeight());
        return new RoundedRectRevealOutlineProvider(radius, radius, fromRect, toRect);
    }

    private void setOnClosingStartCallback(Runnable onClosingStartCallback) {
        mOnClosingStartCallback = onClosingStartCallback;
    }
}
