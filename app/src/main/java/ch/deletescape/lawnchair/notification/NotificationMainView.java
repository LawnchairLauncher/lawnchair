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

package ch.deletescape.lawnchair.notification;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.util.Themes;

/**
 * A {@link android.widget.FrameLayout} that contains a single notification,
 * e.g. icon + title + text.
 */
public class NotificationMainView extends FrameLayout implements SwipeHelper.Callback {

    private NotificationInfo mNotificationInfo;
    private ViewGroup mTextAndBackground;
    private int mBackgroundColor;
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

        mTextAndBackground = findViewById(R.id.text_and_background);
        ColorDrawable colorBackground = (ColorDrawable) mTextAndBackground.getBackground();
        mBackgroundColor = colorBackground.getColor();
        RippleDrawable rippleBackground = new RippleDrawable(ColorStateList.valueOf(
                Themes.getAttrColor(getContext(), android.R.attr.colorControlHighlight)),
                colorBackground, null);
        mTextAndBackground.setBackground(rippleBackground);
        mTitleView = mTextAndBackground.findViewById(R.id.title);
        mTextView = mTextAndBackground.findViewById(R.id.text);
    }

    public void applyNotificationInfo(NotificationInfo mainNotification, View iconView) {
        applyNotificationInfo(mainNotification, iconView, false);
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
            mTitleView.setText(title);
            mTextView.setText(text);
        } else {
            mTitleView.setMaxLines(2);
            mTitleView.setText(TextUtils.isEmpty(title) ? text : title);
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


    // SwipeHelper.Callback's

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return this;
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return mNotificationInfo != null && mNotificationInfo.dismissable;
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