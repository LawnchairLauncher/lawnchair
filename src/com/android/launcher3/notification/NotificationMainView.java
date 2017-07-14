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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.Themes;

/**
 * A {@link android.widget.FrameLayout} that contains a single notification,
 * e.g. icon + title + text.
 */
public class NotificationMainView extends FrameLayout implements SwipeDetector.Listener {

    private NotificationInfo mNotificationInfo;
    private ViewGroup mTextAndBackground;
    private int mBackgroundColor;
    private TextView mTitleView;
    private TextView mTextView;

    private SwipeDetector mSwipeDetector;

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

        mTextAndBackground = (ViewGroup) findViewById(R.id.text_and_background);
        ColorDrawable colorBackground = (ColorDrawable) mTextAndBackground.getBackground();
        mBackgroundColor = colorBackground.getColor();
        RippleDrawable rippleBackground = new RippleDrawable(ColorStateList.valueOf(
                Themes.getAttrColor(getContext(), android.R.attr.colorControlHighlight)),
                colorBackground, null);
        mTextAndBackground.setBackground(rippleBackground);
        mTitleView = (TextView) mTextAndBackground.findViewById(R.id.title);
        mTextView = (TextView) mTextAndBackground.findViewById(R.id.text);
    }

    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView) {
        applyNotificationInfo(mainNotification, iconView, false);
    }

    public void setSwipeDetector(SwipeDetector swipeDetector) {
        mSwipeDetector = swipeDetector;
    }

    /**
     * Sets the content of this view, animating it after a new icon shifts up if necessary.
     */
    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView,
           boolean animate) {
        mNotificationInfo = mainNotification;
        CharSequence title = mNotificationInfo.title;
        CharSequence text = mNotificationInfo.text;
        if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(text)) {
            mTitleView.setText(title.toString());
            mTextView.setText(text.toString());
        } else {
            mTitleView.setMaxLines(2);
            mTitleView.setText(TextUtils.isEmpty(title) ? text.toString() : title.toString());
            mTextView.setVisibility(GONE);
        }
        iconView.setBackground(mNotificationInfo.getIconForBackground(getContext(),
                mBackgroundColor));
        if (mNotificationInfo.intent != null) {
            setOnClickListener(mNotificationInfo);
        }
        setTranslationX(0);
        // Add a dummy ItemInfo so that logging populates the correct container and item types
        // instead of DEFAULT_CONTAINERTYPE and DEFAULT_ITEMTYPE, respectively.
        setTag(new ItemInfo());
        if (animate) {
            ObjectAnimator.ofFloat(mTextAndBackground, ALPHA, 0, 1).setDuration(150).start();
        }
    }

    public NotificationInfo getNotificationInfo() {
        return mNotificationInfo;
    }


    public boolean canChildBeDismissed() {
        return mNotificationInfo != null && mNotificationInfo.dismissable;
    }

    public void onChildDismissed() {
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.getPopupDataProvider().cancelNotification(
                mNotificationInfo.notificationKey);
        launcher.getUserEventDispatcher().logActionOnItem(
                LauncherLogProto.Action.Touch.SWIPE,
                LauncherLogProto.Action.Direction.RIGHT, // Assume all swipes are right for logging.
                LauncherLogProto.ItemType.NOTIFICATION);
    }

    // SwipeDetector.Listener's
    @Override
    public void onDragStart(boolean start) { }


    @Override
    public boolean onDrag(float displacement, float velocity) {
        setTranslationX(canChildBeDismissed()
                ? displacement : OverScroll.dampedScroll(displacement, getWidth()));
        animate().cancel();
        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final boolean willExit;
        final float endTranslation;

        if (!canChildBeDismissed()) {
            willExit = false;
            endTranslation = 0;
        } else if (fling) {
            willExit = true;
            endTranslation = velocity < 0 ? - getWidth() : getWidth();
        } else if (Math.abs(getTranslationX()) > getWidth() / 2) {
            willExit = true;
            endTranslation = (getTranslationX() < 0 ? -getWidth() : getWidth());
        } else {
            willExit = false;
            endTranslation = 0;
        }

        SwipeDetector.ScrollInterpolator interpolator = new SwipeDetector.ScrollInterpolator();
        interpolator.setVelocityAtZero(velocity);

        long duration = SwipeDetector.calculateDuration(velocity,
                (endTranslation - getTranslationX()) / getWidth());
        animate()
                .setDuration(duration)
                .setInterpolator(interpolator)
                .translationX(endTranslation)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeDetector.finishedScrolling();
                        if (willExit) {
                            onChildDismissed();
                        }
                    }
                }).start();
    }
}
