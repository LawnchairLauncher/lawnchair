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

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

/**
 * Education view about widgets.
 */
public class WidgetsEduView extends AbstractSlideInView<Launcher> implements Insettable {

    private static final int DEFAULT_CLOSE_DURATION = 200;

    private Rect mInsets = new Rect();
    private View mEduView;


    public WidgetsEduView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public WidgetsEduView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContent = this;
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
        mEduView = findViewById(R.id.edu_view);
        findViewById(R.id.edu_close_button)
                .setOnClickListener(v -> close(/* animate= */ true));
    }

    @Override
    public void setInsets(Rect insets) {
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(leftInset, getPaddingTop(), rightInset, 0);
        mEduView.setPaddingRelative(mEduView.getPaddingStart(),
                mEduView.getPaddingTop(), mEduView.getPaddingEnd(), bottomInset);
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
        super.onLayout(changed, l, t, r, b);
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

    /** Shows widget education dialog. */
    public static WidgetsEduView showEducationDialog(Launcher launcher) {
        LayoutInflater layoutInflater = LayoutInflater.from(launcher);
        WidgetsEduView v = (WidgetsEduView) layoutInflater.inflate(
                R.layout.widgets_edu, launcher.getDragLayer(), false);
        v.show();
        return v;
    }
}
