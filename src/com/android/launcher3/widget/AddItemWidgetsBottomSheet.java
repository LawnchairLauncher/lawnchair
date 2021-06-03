/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.widget;

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.AddItemActivity;
import com.android.launcher3.views.AbstractSlideInView;

/**
 * Bottom sheet for the pin widget.
 */
public class AddItemWidgetsBottomSheet extends AbstractSlideInView<AddItemActivity>
        implements Insettable {

    private static final int DEFAULT_CLOSE_DURATION = 200;

    private Rect mInsets;
    private Configuration mCurrentConfiguration;

    public AddItemWidgetsBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AddItemWidgetsBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContent = this;
        mInsets = new Rect();
        mCurrentConfiguration = new Configuration(getResources().getConfiguration());
    }

    /**
     * Attaches to activity container and animates open the bottom sheet.
     */
    public void show() {
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(this);
        }
        attachToContainer();
        animateOpen();
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

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(@FloatingViewType int type) {
        return (type & TYPE_PIN_WIDGET_FROM_EXTERNAL_POPUP) != 0;
    }

    @Override
    public void setInsets(Rect insets) {
        // Extend behind left, right, and bottom insets.
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(leftInset, getPaddingTop(), rightInset, bottomInset);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        if (mCurrentConfiguration.orientation != newConfig.orientation) {
            mInsets.setEmpty();
        }
        mCurrentConfiguration.updateFrom(newConfig);
    }

    @Override
    protected int getScrimColor(Context context) {
        return context.getResources().getColor(R.color.widgets_picker_scrim);
    }
}
