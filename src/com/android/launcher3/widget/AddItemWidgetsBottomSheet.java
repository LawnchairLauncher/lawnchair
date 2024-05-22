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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.ScrollView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.AddItemActivity;
import com.android.launcher3.views.AbstractSlideInView;

/**
 * Bottom sheet for the pin widget.
 */
public class AddItemWidgetsBottomSheet extends AbstractSlideInView<AddItemActivity> implements
        View.OnApplyWindowInsetsListener {

    private static final int DEFAULT_CLOSE_DURATION = 200;

    private final Rect mInsets;
    private ScrollView mWidgetPreviewScrollView;

    private int mContentHorizontalMarginInPx;

    public AddItemWidgetsBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AddItemWidgetsBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mInsets = new Rect();
        mContentHorizontalMarginInPx = getResources().getDimensionPixelSize(
                R.dimen.widget_list_horizontal_margin);
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
        setOnApplyWindowInsetsListener(this);
        animateOpen();
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            // Suppress drag to dismiss gesture if the scroll view is being scrolled.
            if (getPopupContainer().isEventOverView(mWidgetPreviewScrollView, ev)
                    && mWidgetPreviewScrollView.getScrollY() > 0) {
                mNoIntercept = true;
            }
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        // Lay out content as center bottom aligned.
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
        if (deviceProfile.isTablet) {
            int margin = deviceProfile.allAppsLeftRightMargin;
            widthUsed = Math.max(2 * margin, 2 * (mInsets.left + mInsets.right));
        } else if (mInsets.bottom > 0) {
            widthUsed = mInsets.left + mInsets.right;
        } else {
            Rect padding = deviceProfile.workspacePadding;
            widthUsed = Math.max(padding.left + padding.right,
                    2 * (mInsets.left + mInsets.right));
        }

        measureChildWithMargins(mContent, widthMeasureSpec,
                widthUsed, heightMeasureSpec, deviceProfile.bottomSheetTopPadding);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.add_item_bottom_sheet_content);
        mWidgetPreviewScrollView = findViewById(R.id.widget_preview_scroll_view);
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;
        setUpDefaultOpenAnimation().start();
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
    protected int getScrimColor(Context context) {
        // Don't add a scrim when using the standalone picker activity. The background dimming is
        // handled by applying dimBackground in the activity theme, so the scrim doesn't slide in
        // with the window.
        return -1;
    }

    @SuppressLint("NewApi") // Already added API check.
    @Override
    public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
        Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
        mInsets.set(insets.left, insets.top, insets.right, insets.bottom);
        mContent.setPadding(mContent.getPaddingStart(), mContent.getPaddingTop(),
                mContent.getPaddingEnd(), mInsets.bottom);

        int contentHorizontalMarginInPx = getResources().getDimensionPixelSize(
                R.dimen.widget_list_horizontal_margin);
        if (contentHorizontalMarginInPx != mContentHorizontalMarginInPx) {
            setContentHorizontalMargin(findViewById(R.id.widget_appName),
                    contentHorizontalMarginInPx);
            setContentHorizontalMargin(findViewById(R.id.widget_drag_instruction),
                    contentHorizontalMarginInPx);
            setContentHorizontalMargin(findViewById(R.id.widget_cell), contentHorizontalMarginInPx);
            setContentHorizontalMargin(findViewById(R.id.actions_container),
                    contentHorizontalMarginInPx);
            mContentHorizontalMarginInPx = contentHorizontalMarginInPx;
        }
        return windowInsets;
    }

    private static void setContentHorizontalMargin(View view, int contentHorizontalMargin) {
        ViewGroup.MarginLayoutParams layoutParams =
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams());
        layoutParams.setMarginStart(contentHorizontalMargin);
        layoutParams.setMarginEnd(contentHorizontalMargin);
    }
}
