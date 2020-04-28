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

import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.Themes;

/**
 * A {@link android.widget.FrameLayout} that contains a single notification,
 * e.g. icon + title + text.
 */
@TargetApi(Build.VERSION_CODES.N)
public class NotificationMainView extends FrameLayout implements SingleAxisSwipeDetector.Listener {

    private static FloatProperty<NotificationMainView> CONTENT_TRANSLATION =
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

    private final ObjectAnimator mContentTranslateAnimator;

    private NotificationInfo mNotificationInfo;
    private ViewGroup mTextAndBackground;
    private int mBackgroundColor;
    private TextView mTitleView;
    private TextView mTextView;
    private View mIconView;

    private SingleAxisSwipeDetector mSwipeDetector;

    public NotificationMainView(Context context) {
        this(context, null, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContentTranslateAnimator = ObjectAnimator.ofFloat(this, CONTENT_TRANSLATION, 0);
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
        mIconView = findViewById(R.id.popup_item_icon);
    }

    public void setSwipeDetector(SingleAxisSwipeDetector swipeDetector) {
        mSwipeDetector = swipeDetector;
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
        // Add a dummy ItemInfo so that logging populates the correct container and item types
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

    public void setContentVisibility(int visibility) {
        mTextAndBackground.setVisibility(visibility);
        mIconView.setVisibility(visibility);
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

    // SingleAxisSwipeDetector.Listener's
    @Override
    public void onDragStart(boolean start) { }


    @Override
    public boolean onDrag(float displacement) {
        setContentTranslation(canChildBeDismissed()
                ? displacement : OverScroll.dampedScroll(displacement, getWidth()));
        mContentTranslateAnimator.cancel();
        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        final boolean willExit;
        final float endTranslation;
        final float startTranslation = mTextAndBackground.getTranslationX();

        if (!canChildBeDismissed()) {
            willExit = false;
            endTranslation = 0;
        } else if (mSwipeDetector.isFling(velocity)) {
            willExit = true;
            endTranslation = velocity < 0 ? - getWidth() : getWidth();
        } else if (Math.abs(startTranslation) > getWidth() / 2) {
            willExit = true;
            endTranslation = (startTranslation < 0 ? -getWidth() : getWidth());
        } else {
            willExit = false;
            endTranslation = 0;
        }

        long duration = BaseSwipeDetector.calculateDuration(velocity,
                (endTranslation - startTranslation) / getWidth());

        mContentTranslateAnimator.removeAllListeners();
        mContentTranslateAnimator.setDuration(duration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
        mContentTranslateAnimator.setFloatValues(startTranslation, endTranslation);
        mContentTranslateAnimator.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mSwipeDetector.finishedScrolling();
                if (willExit) {
                    onChildDismissed();
                }
            }
        });
        mContentTranslateAnimator.start();
    }
}
