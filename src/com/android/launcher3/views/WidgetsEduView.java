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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;

/**
 * Education view about widgets.
 */
public class WidgetsEduView extends AbstractSlideInView<BaseActivity> implements Insettable {

    private static final int DEFAULT_CLOSE_DURATION = 200;

    private Rect mInsets = new Rect();

    public WidgetsEduView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public WidgetsEduView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(true, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_WIDGETS_EDUCATION_DIALOG) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.edu_view);
        findViewById(R.id.edu_close_button)
                .setOnClickListener(v -> close(/* animate= */ true));
        setContentBackgroundWithParent(mContent.getBackground(), mContent);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        mContent.setPadding(mContent.getPaddingStart(),
                mContent.getPaddingTop(), mContent.getPaddingEnd(), insets.bottom);
    }

    @Override
    protected void onScaleProgressChanged() {
        super.onScaleProgressChanged();
        setTranslationY(getMeasuredHeight() * (1 - mSlideInViewScale.value) / 2);
    }

    private void show() {
        attachToContainer();
        animateOpen();
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int widthUsed;
        if (mInsets.bottom > 0) {
            // Extra space between this view and mContent horizontally when the sheet is shown in
            // portrait mode.
            widthUsed = mInsets.left + mInsets.right;
        } else {
            // Extra space between this view and mContent horizontally when the sheet is shown in
            // landscape mode.
            Rect padding = deviceProfile.workspacePadding;
            widthUsed = Math.max(padding.left + padding.right,
                    2 * (mInsets.left + mInsets.right));
        }

        int heightUsed = mInsets.top + deviceProfile.edgeMarginPx;
        measureChildWithMargins(mContent, widthMeasureSpec,
                widthUsed, heightMeasureSpec, heightUsed);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;
        setUpDefaultOpenAnimation().start();
    }

    /** Shows widget education dialog. */
    public static WidgetsEduView showEducationDialog(BaseActivity activity) {
        LayoutInflater layoutInflater = LayoutInflater.from(activity);
        WidgetsEduView v = (WidgetsEduView) layoutInflater.inflate(
                R.layout.widgets_edu, activity.getDragLayer(), false);
        v.show();
        return v;
    }
}
