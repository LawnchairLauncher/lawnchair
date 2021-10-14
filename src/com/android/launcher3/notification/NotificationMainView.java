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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DISMISSED;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.touch.SingleAxisSwipeDetector;

/**
 * A {@link android.widget.FrameLayout} that contains a single notification,
 * e.g. icon + title + text.
 */
@TargetApi(Build.VERSION_CODES.N)
public class NotificationMainView extends FrameLayout {

    private static final FloatProperty<NotificationMainView> CONTENT_TRANSLATION =
            new FloatProperty<NotificationMainView>("contentTranslation") {
        @Override
        public void setValue(NotificationMainView view, float v) {
            view.setContentTranslation(v);
        }

        @Override
        public Float get(NotificationMainView view) {
            return view.mTextAndBackground.getTranslationX();
        }
    };

    // This is used only to track the notification view, so that it can be properly logged.
    public static final ItemInfo NOTIFICATION_ITEM_INFO = new ItemInfo();

    private NotificationInfo mNotificationInfo;
    private ViewGroup mTextAndBackground;
    private int mBackgroundColor;
    private TextView mTitleView;
    private TextView mTextView;
    private View mIconView;

    private SingleAxisSwipeDetector mSwipeDetector;

    private final ColorDrawable mColorDrawable;

    public NotificationMainView(Context context) {
        this(context, null, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mColorDrawable = new ColorDrawable(Color.TRANSPARENT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextAndBackground = findViewById(R.id.text_and_background);
        mTitleView = mTextAndBackground.findViewById(R.id.title);
        mTextView = mTextAndBackground.findViewById(R.id.text);
        mIconView = findViewById(R.id.popup_item_icon);

        ColorDrawable colorBackground = (ColorDrawable) mTextAndBackground.getBackground();
        updateBackgroundColor(colorBackground.getColor());
    }

    private void updateBackgroundColor(int color) {
        mBackgroundColor = color;
        mColorDrawable.setColor(color);
        mTextAndBackground.setBackground(mColorDrawable);
        if (mNotificationInfo != null) {
            mIconView.setBackground(mNotificationInfo.getIconForBackground(getContext(),
                    mBackgroundColor));
        }
    }

    /**
     * Animates the background color to a new color.
     * @param color The color to change to.
     * @param animatorSetOut The AnimatorSet where we add the color animator to.
     */
    public void updateBackgroundColor(int color, AnimatorSet animatorSetOut) {
        int oldColor = mBackgroundColor;
        ValueAnimator colors = ValueAnimator.ofArgb(oldColor, color);
        colors.addUpdateListener(valueAnimator -> {
            int newColor = (int) valueAnimator.getAnimatedValue();
            updateBackgroundColor(newColor);
        });
        animatorSetOut.play(colors);
    }

    /**
     * Sets the content of this view, animating it after a new icon shifts up if necessary.
     */
    public void applyNotificationInfo(NotificationInfo mainNotification, boolean animate) {
        mNotificationInfo = mainNotification;
        NotificationListener listener = NotificationListener.getInstanceIfConnected();
        if (listener != null) {
            listener.setNotificationsShown(new String[] {mNotificationInfo.notificationKey});
        }
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
        mIconView.setBackground(mNotificationInfo.getIconForBackground(getContext(),
                mBackgroundColor));
        if (mNotificationInfo.intent != null) {
            setOnClickListener(mNotificationInfo);
        }
        setContentTranslation(0);
        // Add a stub ItemInfo so that logging populates the correct container and item types
        // instead of DEFAULT_CONTAINERTYPE and DEFAULT_ITEMTYPE, respectively.
        setTag(NOTIFICATION_ITEM_INFO);
        if (animate) {
            ObjectAnimator.ofFloat(mTextAndBackground, ALPHA, 0, 1).setDuration(150).start();
        }
    }

    public void setContentTranslation(float translation) {
        mTextAndBackground.setTranslationX(translation);
        mIconView.setTranslationX(translation);
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
        launcher.getStatsLogManager().logger().log(LAUNCHER_NOTIFICATION_DISMISSED);
    }
}
