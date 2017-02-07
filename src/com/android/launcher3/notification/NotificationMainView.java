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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherViewPropertyAnimator;
import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.userevent.nano.LauncherLogProto;

/**
 * A {@link LinearLayout} that contains a single notification, e.g. icon + title + text.
 */
public class NotificationMainView extends LinearLayout implements SwipeHelper.Callback {

    private NotificationInfo mNotificationInfo;
    private TextView mTitleView;
    private TextView mTextView;
    private IconPalette mIconPalette;

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

    public void applyColors(IconPalette iconPalette) {
        setBackgroundColor(iconPalette.backgroundColor);
        mIconPalette = iconPalette;
    }

    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView) {
        applyNotificationInfo(mainNotification, iconView, false);
    }

    /**
     * Sets the content of this view, animating it after a new icon shifts up if necessary.
     */
    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView,
           boolean animate) {
        if (animate) {
            mTitleView.setAlpha(0);
            mTextView.setAlpha(0);
            setBackgroundColor(mIconPalette.secondaryColor);
        }
        mNotificationInfo = mainNotification;
        mTitleView.setText(mNotificationInfo.title);
        mTextView.setText(mNotificationInfo.text);
        iconView.setBackground(mNotificationInfo.getIconForBackground(
                getContext(), mIconPalette.backgroundColor));
        setOnClickListener(mNotificationInfo);
        setTranslationX(0);
        // Add a dummy ItemInfo so that logging populates the correct container and item types
        // instead of DEFAULT_CONTAINERTYPE and DEFAULT_ITEMTYPE, respectively.
        setTag(new ItemInfo());
        if (animate) {
            AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
            Animator textFade = new LauncherViewPropertyAnimator(mTextView).alpha(1);
            Animator titleFade = new LauncherViewPropertyAnimator(mTitleView).alpha(1);
            ValueAnimator colorChange = ValueAnimator.ofArgb(mIconPalette.secondaryColor,
                    mIconPalette.backgroundColor);
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
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.getPopupDataProvider().cancelNotification(
                mNotificationInfo.notificationKey);
        launcher.getUserEventDispatcher().logActionOnItem(
                LauncherLogProto.Action.Touch.SWIPE,
                LauncherLogProto.Action.Direction.RIGHT, // Assume all swipes are right for logging.
                LauncherLogProto.ItemType.NOTIFICATION);
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
