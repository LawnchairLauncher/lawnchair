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

package com.android.launcher3.notification;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherViewPropertyAnimator;
import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;

/**
 * A {@link LinearLayout} that contains a single notification, e.g. icon + title + text.
 */
public class NotificationMainView extends LinearLayout implements SwipeHelper.Callback {

    private NotificationInfo mNotificationInfo;
    private TextView mTitleView;
    private TextView mTextView;

    public NotificationMainView(Context context) {
        this(context, null, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitleView = (TextView) findViewById(R.id.title);
        mTextView = (TextView) findViewById(R.id.text);
    }

    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView) {
        applyNotificationInfo(mainNotification, iconView, null);
    }

    /**
     * @param iconPalette if not null, indicates that the new info should be animated in,
     *                    and that part of this animation includes animating the background
     *                    from iconPalette.secondaryColor to iconPalette.backgroundColor.
     */
    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView,
            @Nullable IconPalette iconPalette) {
        boolean animate = iconPalette != null;
        if (animate) {
            mTitleView.setAlpha(0);
            mTextView.setAlpha(0);
            setBackgroundColor(iconPalette.secondaryColor);
        }
        mNotificationInfo = mainNotification;
        mTitleView.setText(mNotificationInfo.title);
        mTextView.setText(mNotificationInfo.text);
        iconView.setBackground(mNotificationInfo.iconDrawable);
        setOnClickListener(mNotificationInfo);
        setTranslationX(0);
        if (animate) {
            AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
            Animator textFade = new LauncherViewPropertyAnimator(mTextView).alpha(1);
            Animator titleFade = new LauncherViewPropertyAnimator(mTitleView).alpha(1);
            ValueAnimator colorChange = ValueAnimator.ofArgb(iconPalette.secondaryColor,
                    iconPalette.backgroundColor);
            colorChange.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    setBackgroundColor((Integer) valueAnimator.getAnimatedValue());
                }
            });
            animation.playTogether(textFade, titleFade, colorChange);
            animation.setDuration(150);
            animation.start();
        }
    }

    public NotificationInfo getNotificationInfo() {
        return mNotificationInfo;
    }


    // SwipeHelper.Callback's

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return this;
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return mNotificationInfo.dismissable;
    }

    @Override
    public boolean isAntiFalsingNeeded() {
        return false;
    }

    @Override
    public void onBeginDrag(View v) {
    }

    @Override
    public void onChildDismissed(View v) {
        Launcher.getLauncher(getContext()).getPopupDataProvider().cancelNotification(
                mNotificationInfo.notificationKey);
    }

    @Override
    public void onDragCancelled(View v) {
    }

    @Override
    public void onChildSnappedBack(View animView, float targetLeft) {
    }

    @Override
    public boolean updateSwipeProgress(View animView, boolean dismissable, float swipeProgress) {
        // Don't fade out.
        return true;
    }

    @Override
    public float getFalsingThresholdFactor() {
        return 1;
    }
}
