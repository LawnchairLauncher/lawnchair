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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DISMISSED;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

/**
 * A {@link android.widget.FrameLayout} that contains a single notification,
 * e.g. icon + title + text.
 */
@TargetApi(Build.VERSION_CODES.N)
public class NotificationMainView extends LinearLayout {

    // This is used only to track the notification view, so that it can be properly logged.
    public static final ItemInfo NOTIFICATION_ITEM_INFO = new ItemInfo();

    // Value when the primary notification main view will be gone (zero alpha).
    private static final float PRIMARY_GONE_PROGRESS = 0.7f;
    private static final float PRIMARY_MIN_PROGRESS = 0.40f;
    private static final float PRIMARY_MAX_PROGRESS = 0.60f;
    private static final float SECONDARY_MIN_PROGRESS = 0.30f;
    private static final float SECONDARY_MAX_PROGRESS = 0.50f;
    private static final float SECONDARY_CONTENT_MAX_PROGRESS = 0.6f;

    private NotificationInfo mNotificationInfo;
    private int mBackgroundColor;
    private TextView mTitleView;
    private TextView mTextView;
    private View mIconView;

    private View mHeader;
    private View mMainView;

    private TextView mHeaderCount;
    private final Rect mOutline = new Rect();

    // Space between notifications during swipe
    private final int mNotificationSpace;
    private final int mMaxTransX;
    private final int mMaxElevation;

    private final GradientDrawable mBackground;

    public NotificationMainView(Context context) {
        this(context, null, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public NotificationMainView(Context context, AttributeSet attrs, int defStyle, int defStylRes) {
        super(context, attrs, defStyle, defStylRes);

        float outlineRadius = Themes.getDialogCornerRadius(context);

        mBackground = new GradientDrawable();
        mBackground.setColor(Themes.getAttrColor(context, R.attr.popupColorPrimary));
        mBackground.setCornerRadius(outlineRadius);
        setBackground(mBackground);

        mMaxElevation = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_elevation);
        setElevation(mMaxElevation);

        mMaxTransX = getResources().getDimensionPixelSize(R.dimen.notification_max_trans);
        mNotificationSpace = getResources().getDimensionPixelSize(R.dimen.notification_space);

        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(mOutline, outlineRadius);
            }
        });
    }

    /**
     * Updates the header text.
     * @param notificationCount The number of notifications.
     */
    public void updateHeader(int notificationCount) {
        final String text;
        final int visibility;
        if (notificationCount <= 1) {
            text = "";
            visibility = View.INVISIBLE;
        } else {
            text = String.valueOf(notificationCount);
            visibility = View.VISIBLE;

        }
        mHeaderCount.setText(text);
        mHeaderCount.setVisibility(visibility);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ViewGroup textAndBackground = findViewById(R.id.text_and_background);
        mTitleView = textAndBackground.findViewById(R.id.title);
        mTextView = textAndBackground.findViewById(R.id.text);
        mIconView = findViewById(R.id.popup_item_icon);
        mHeaderCount = findViewById(R.id.notification_count);

        mHeader = findViewById(R.id.header);
        mMainView = findViewById(R.id.main_view);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mOutline.set(0, 0, getWidth(), getHeight());
        invalidateOutline();
    }

    private void updateBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackground.setColor(color);
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
    public void applyNotificationInfo(NotificationInfo notificationInfo) {
        mNotificationInfo = notificationInfo;
        if (notificationInfo == null) {
            return;
        }
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

        // Add a stub ItemInfo so that logging populates the correct container and item types
        // instead of DEFAULT_CONTAINERTYPE and DEFAULT_ITEMTYPE, respectively.
        setTag(NOTIFICATION_ITEM_INFO);
    }

    /**
     * Sets the alpha of only the child views.
     */
    public void setContentAlpha(float alpha) {
        mHeader.setAlpha(alpha);
        mMainView.setAlpha(alpha);
    }

    /**
     * Sets the translation of only the child views.
     */
    public void setContentTranslationX(float transX) {
        mHeader.setTranslationX(transX);
        mMainView.setTranslationX(transX);
    }

    /**
     * Updates the alpha, content alpha, and elevation of this view.
     *
     * @param progress Range from [0, 1] or [-1, 0]
     *                 When 0: Full alpha
     *                 When 1/-1: zero alpha
     */
    public void onPrimaryDrag(float progress) {
        float absProgress = Math.abs(progress);
        final int width = getWidth();

        float min = PRIMARY_MIN_PROGRESS;
        float max = PRIMARY_MAX_PROGRESS;

        if (absProgress < min) {
            setAlpha(1f);
            setContentAlpha(1);
            setElevation(mMaxElevation);
        } else if (absProgress < max) {
            setAlpha(1f);
            setContentAlpha(mapToRange(absProgress, min, max, 1f, 0f, LINEAR));
            setElevation(Utilities.mapToRange(absProgress, min, max, mMaxElevation, 0, LINEAR));
        } else {
            setAlpha(mapToRange(absProgress, max, PRIMARY_GONE_PROGRESS, 1f, 0f, LINEAR));
            setContentAlpha(0f);
            setElevation(0f);
        }

        setTranslationX(width * progress);
    }

    /**
     * Updates the alpha, content alpha, elevation, and clipping of this view.
     * @param progress Range from [0, 1] or [-1, 0]
      *                 When 0: Smallest clipping, zero alpha
      *                 When 1/-1: Full clip, full alpha
     */
    public void onSecondaryDrag(float progress) {
        final float absProgress = Math.abs(progress);

        float min = SECONDARY_MIN_PROGRESS;
        float max = SECONDARY_MAX_PROGRESS;
        float contentMax = SECONDARY_CONTENT_MAX_PROGRESS;

        if (absProgress < min) {
            setAlpha(0f);
            setContentAlpha(0);
            setElevation(0f);
        } else if (absProgress < max) {
            setAlpha(mapToRange(absProgress, min, max, 0, 1f, LINEAR));
            setContentAlpha(0f);
            setElevation(0f);
        } else {
            setAlpha(1f);
            setContentAlpha(absProgress > contentMax
                    ? 1f
                    : mapToRange(absProgress, max, contentMax, 0, 1f, LINEAR));
            setElevation(Utilities.mapToRange(absProgress, max, 1, 0, mMaxElevation, LINEAR));
        }

        final int width = getWidth();
        int crop = (int) (width * absProgress);
        int space = (int) (absProgress > PRIMARY_GONE_PROGRESS
                ? mapToRange(absProgress, PRIMARY_GONE_PROGRESS, 1f, mNotificationSpace, 0, LINEAR)
                : mNotificationSpace);
        if (progress < 0) {
            mOutline.left = Math.max(0, getWidth() - crop + space);
            mOutline.right = getWidth();
        } else {
            mOutline.right = Math.min(getWidth(), crop - space);
            mOutline.left = 0;
        }

        float contentTransX = mMaxTransX * (1f - absProgress);
        setContentTranslationX(progress < 0
                ? contentTransX
                : -contentTransX);
        invalidateOutline();
    }

    public @Nullable NotificationInfo getNotificationInfo() {
        return mNotificationInfo;
    }

    public boolean canChildBeDismissed() {
        return mNotificationInfo != null && mNotificationInfo.dismissable;
    }

    public void onChildDismissed() {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        PopupDataProvider popupDataProvider = activityContext.getPopupDataProvider();
        if (popupDataProvider == null) {
            return;
        }
        popupDataProvider.cancelNotification(mNotificationInfo.notificationKey);
        activityContext.getStatsLogManager().logger().log(LAUNCHER_NOTIFICATION_DISMISSED);
    }
}
