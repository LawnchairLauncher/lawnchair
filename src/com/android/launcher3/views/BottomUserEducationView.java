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
package com.android.launcher3.views;

import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

public class BottomUserEducationView extends AbstractSlideInView implements Insettable {

    private static final String KEY_SHOWED_BOTTOM_USER_EDUCATION = "showed_bottom_user_education";

    private static final int DEFAULT_CLOSE_DURATION = 200;

    private final Rect mInsets = new Rect();

    private View mCloseButton;

    public BottomUserEducationView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public BottomUserEducationView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContent = this;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCloseButton = findViewById(R.id.close_bottom_user_tip);
        mCloseButton.setOnClickListener(view -> handleClose(true));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setTranslationShift(mTranslationShift);
        expandTouchAreaOfCloseButton();
    }

    @Override
    public void logActionCommand(int command) {
        // Since this is on-boarding popup, it is not a user controlled action.
    }

    @Override
    public int getLogContainerType() {
        return ContainerType.TIP;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    @Override
    public void setInsets(Rect insets) {
        // Extend behind left, right, and bottom insets.
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(getPaddingLeft() + leftInset, getPaddingTop(),
                getPaddingRight() + rightInset, getPaddingBottom() + bottomInset);
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
        if (animate) {
            // We animate only when the user is visible, which is a proxy for an explicit
            // close action.
            mLauncher.getSharedPrefs().edit()
                    .putBoolean(KEY_SHOWED_BOTTOM_USER_EDUCATION, true).apply();
            sendCustomAccessibilityEvent(
                    BottomUserEducationView.this,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    getContext().getString(R.string.bottom_work_tab_user_education_closed));
        }
    }

    private void open(boolean animate) {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        if (animate) {
            mOpenCloseAnimator.setValues(
                    PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
            mOpenCloseAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
            mOpenCloseAnimator.start();
        } else {
            setTranslationShift(TRANSLATION_SHIFT_OPENED);
        }
    }

    public static void showIfNeeded(Launcher launcher) {
        if (launcher.getSharedPrefs().getBoolean(KEY_SHOWED_BOTTOM_USER_EDUCATION, false)) {
            return;
        }

        LayoutInflater layoutInflater = LayoutInflater.from(launcher);
        BottomUserEducationView bottomUserEducationView =
                (BottomUserEducationView) layoutInflater.inflate(
                        R.layout.work_tab_bottom_user_education_view, launcher.getDragLayer(),
                        false);
        launcher.getDragLayer().addView(bottomUserEducationView);
        bottomUserEducationView.open(true);
    }

    private void expandTouchAreaOfCloseButton() {
        Rect hitRect = new Rect();
        mCloseButton.getHitRect(hitRect);
        hitRect.left -= mCloseButton.getWidth();
        hitRect.top -= mCloseButton.getHeight();
        hitRect.right += mCloseButton.getWidth();
        hitRect.bottom += mCloseButton.getHeight();
        View parent = (View) mCloseButton.getParent();
        parent.setTouchDelegate(new TouchDelegate(hitRect, mCloseButton));
    }
}
