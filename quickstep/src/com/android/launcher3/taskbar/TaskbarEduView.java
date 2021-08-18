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

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.views.AbstractSlideInView;

/** Education view about the Taskbar. */
public class TaskbarEduView extends AbstractSlideInView<TaskbarActivityContext>
        implements Insettable {

    private static final int DEFAULT_CLOSE_DURATION = 200;

    private final Rect mInsets = new Rect();

    public TaskbarEduView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public TaskbarEduView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASKBAR_EDUCATION_DIALOG) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.edu_view);
        findViewById(R.id.edu_close_button).setOnClickListener(v -> close(true /* animate */));
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

    /** Show the Education flow. */
    public void show() {
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

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(FAST_OUT_SLOW_IN);
        mOpenCloseAnimator.start();
    }
}
