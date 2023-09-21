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
package com.android.launcher3.taskbar;

import static com.android.launcher3.anim.Interpolators.EMPHASIZED;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.Button;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.views.AbstractSlideInView;

/** Education view about the Taskbar. */
public class TaskbarEduView extends AbstractSlideInView<TaskbarOverlayContext>
        implements Insettable {

    private final Rect mInsets = new Rect();

    // Initialized in init.
    private TaskbarEduController.TaskbarEduCallbacks mTaskbarEduCallbacks;

    private Button mStartButton;
    private Button mEndButton;
    private TaskbarEduPagedView mPagedView;

    public TaskbarEduView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public TaskbarEduView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected void init(TaskbarEduController.TaskbarEduCallbacks callbacks) {
        if (mPagedView != null) {
            mPagedView.setControllerCallbacks(callbacks);
        }
        mTaskbarEduCallbacks = callbacks;
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, mTaskbarEduCallbacks.getCloseDuration());
    }

    @Override
    protected Interpolator getIdleInterpolator() {
        return EMPHASIZED;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASKBAR_EDUCATION_DIALOG) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.edu_view);
        mStartButton = findViewById(R.id.edu_start_button);
        mEndButton = findViewById(R.id.edu_end_button);
        mPagedView = findViewById(R.id.content);
        mPagedView.setTaskbarEduView(this);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        mContent.setPadding(mContent.getPaddingStart(),
                mContent.getPaddingTop(), mContent.getPaddingEnd(), insets.bottom);
    }

    @Override
    protected void attachToContainer() {
        if (mColorScrim != null) {
            getPopupContainer().addView(mColorScrim, 0);
        }
        getPopupContainer().addView(this, 1);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int contentWidth = Math.min(getContentAreaWidth(), getMeasuredWidth());
        contentWidth = Math.max(contentWidth, mTaskbarEduCallbacks.getIconLayoutBoundsWidth());
        int contentAreaWidthSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY);

        mContent.measure(contentAreaWidthSpec, MeasureSpec.UNSPECIFIED);
    }

    private int getContentAreaWidth() {
        return mTaskbarEduCallbacks.getIconLayoutBoundsWidth()
                + getResources().getDimensionPixelSize(R.dimen.taskbar_edu_horizontal_margin) * 2;
    }

    /** Show the Education flow. */
    public void show() {
        attachToContainer();
        animateOpen();
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(mContent, mIsOpen ? getContext().getString(R.string.taskbar_edu_opened)
                : getContext().getString(R.string.taskbar_edu_closed));
    }

    @Override
    protected int getScrimColor(Context context) {
        return context.getResources().getColor(R.color.widgets_picker_scrim);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        // Lay out the content as center bottom aligned.
        int contentWidth = mContent.getMeasuredWidth();
        int contentLeft = (width - contentWidth - mInsets.left - mInsets.right) / 2 + mInsets.left;
        mContent.layout(contentLeft, height - mContent.getMeasuredHeight(),
                contentLeft + contentWidth, height);

        setTranslationShift(mTranslationShift);
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(EMPHASIZED);
        mOpenCloseAnimator.setDuration(mTaskbarEduCallbacks.getOpenDuration()).start();
    }

    void snapToPage(int page) {
        mPagedView.snapToPage(page);
    }

    void updateStartButton(int textResId, OnClickListener onClickListener) {
        mStartButton.setText(textResId);
        mStartButton.setOnClickListener(onClickListener);
    }

    void updateEndButton(int textResId, OnClickListener onClickListener) {
        mEndButton.setText(textResId);
        mEndButton.setOnClickListener(onClickListener);
    }
}
