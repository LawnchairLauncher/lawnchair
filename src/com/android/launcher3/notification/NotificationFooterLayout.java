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
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherViewPropertyAnimator;
import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.popup.PopupContainerWithArrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link LinearLayout} that contains only icons of notifications.
 * If there are more than {@link #MAX_FOOTER_NOTIFICATIONS} icons, we add a "+x" overflow.
 */
public class NotificationFooterLayout extends LinearLayout {

    public interface IconAnimationEndListener {
        void onIconAnimationEnd(NotificationInfo animatedNotification);
    }

    private static final int MAX_FOOTER_NOTIFICATIONS = 5;

    private static final Rect sTempRect = new Rect();

    private final List<NotificationInfo> mNotifications = new ArrayList<>();
    private final List<NotificationInfo> mOverflowNotifications = new ArrayList<>();
    private final Map<View, NotificationInfo> mViewsToInfos = new HashMap<>();

    LinearLayout.LayoutParams mIconLayoutParams;
    private LinearLayout mIconRow;
    private int mTextColor;

    public NotificationFooterLayout(Context context) {
        this(context, null, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        int size = getResources().getDimensionPixelSize(
                R.dimen.notification_footer_icon_size);
        int padding = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcut_drawable_padding);
        mIconLayoutParams = new LayoutParams(size, size);
        mIconLayoutParams.setMarginStart(padding);
        mIconLayoutParams.gravity = Gravity.CENTER_VERTICAL;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconRow = (LinearLayout) findViewById(R.id.icon_row);
    }

    public void applyColors(IconPalette iconPalette) {
        setBackgroundTintList(ColorStateList.valueOf(iconPalette.backgroundColor));
        findViewById(R.id.divider).setBackgroundColor(iconPalette.secondaryColor);
        mTextColor = iconPalette.textColor;
    }

    /**
     * Keep track of the NotificationInfo, and then update the UI when
     * {@link #commitNotificationInfos()} is called.
     */
    public void addNotificationInfo(final NotificationInfo notificationInfo) {
        if (mNotifications.size() < MAX_FOOTER_NOTIFICATIONS) {
            mNotifications.add(notificationInfo);
        } else {
            mOverflowNotifications.add(notificationInfo);
        }
    }

    /**
     * Adds icons and potentially overflow text for all of the NotificationInfo's
     * added using {@link #addNotificationInfo(NotificationInfo)}.
     */
    public void commitNotificationInfos() {
        mIconRow.removeAllViews();
        mViewsToInfos.clear();

        for (int i = 0; i < mNotifications.size(); i++) {
            NotificationInfo info = mNotifications.get(i);
            addNotificationIconForInfo(info, false /* fromOverflow */);
        }

        if (!mOverflowNotifications.isEmpty()) {
            TextView overflowText = new TextView(getContext());
            overflowText.setTextColor(mTextColor);
            updateOverflowText(overflowText);
            mIconRow.addView(overflowText, mIconLayoutParams);
        }
    }

    private void addNotificationIconForInfo(NotificationInfo info, boolean fromOverflow) {
        View icon = new View(getContext());
        icon.setBackground(info.iconDrawable);
        icon.setOnClickListener(info);
        int addIndex = mIconRow.getChildCount();
        if (fromOverflow) {
            // Add the notification before the overflow view.
            addIndex--;
            icon.setAlpha(0);
            icon.animate().alpha(1);
        }
        mIconRow.addView(icon, addIndex, mIconLayoutParams);
        mViewsToInfos.put(icon, info);
    }

    private void updateOverflowText(TextView overflowTextView) {
        overflowTextView.setText(getResources().getString(R.string.deep_notifications_overflow,
                mOverflowNotifications.size()));
    }

    public void animateFirstNotificationTo(Rect toBounds,
            final IconAnimationEndListener callback) {
        AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final View firstNotification = mIconRow.getChildAt(0);

        Rect fromBounds = sTempRect;
        firstNotification.getGlobalVisibleRect(fromBounds);
        float scale = (float) toBounds.height() / fromBounds.height();
        Animator moveAndScaleIcon = new LauncherViewPropertyAnimator(firstNotification)
                .translationY(toBounds.top - fromBounds.top
                        + (fromBounds.height() * scale - fromBounds.height()) / 2)
                .scaleX(scale).scaleY(scale);
        moveAndScaleIcon.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                callback.onIconAnimationEnd(mViewsToInfos.get(firstNotification));
            }
        });
        animation.play(moveAndScaleIcon);

        // Shift all notifications (not the overflow) over to fill the gap.
        int gapWidth = mIconLayoutParams.width + mIconLayoutParams.getMarginStart();
        int numIcons = mIconRow.getChildCount()
                - (mOverflowNotifications.isEmpty() ? 0 : 1);
        for (int i = 1; i < numIcons; i++) {
            final View child = mIconRow.getChildAt(i);
            Animator shiftChild = new LauncherViewPropertyAnimator(child).translationX(-gapWidth);
            shiftChild.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // We have to set the translation X to 0 when the new main notification
                    // is removed from the footer.
                    // TODO: remove it here instead of expecting trimNotifications to do so.
                    child.setTranslationX(0);
                }
            });
            animation.play(shiftChild);
        }
        animation.start();
    }

    public void trimNotifications(Set<String> notifications) {
        if (!isAttachedToWindow() || mIconRow.getChildCount() == 0) {
            return;
        }
        Iterator<NotificationInfo> overflowIterator = mOverflowNotifications.iterator();
        while (overflowIterator.hasNext()) {
            if (!notifications.contains(overflowIterator.next().notificationKey)) {
                overflowIterator.remove();
            }
        }
        TextView overflowView = null;
        for (int i = mIconRow.getChildCount() - 1; i >= 0; i--) {
            View child = mIconRow.getChildAt(i);
            if (child instanceof TextView) {
                overflowView = (TextView) child;
            } else {
                NotificationInfo childInfo = mViewsToInfos.get(child);
                if (!notifications.contains(childInfo.notificationKey)) {
                    mIconRow.removeView(child);
                    mNotifications.remove(childInfo);
                    mViewsToInfos.remove(child);
                    if (!mOverflowNotifications.isEmpty()) {
                        NotificationInfo notification = mOverflowNotifications.remove(0);
                        mNotifications.add(notification);
                        addNotificationIconForInfo(notification, true /* fromOverflow */);
                    }
                }
            }
        }
        if (overflowView != null) {
            if (mOverflowNotifications.isEmpty()) {
                mIconRow.removeView(overflowView);
            } else {
                updateOverflowText(overflowView);
            }
        }
        if (mIconRow.getChildCount() == 0) {
            // There are no more icons in the secondary view, so hide it.
            PopupContainerWithArrow popup = PopupContainerWithArrow.getOpen(
                    Launcher.getLauncher(getContext()));
            int newHeight = getResources().getDimensionPixelSize(
                    R.dimen.notification_footer_collapsed_height);
            AnimatorSet collapseSecondary = LauncherAnimUtils.createAnimatorSet();
            collapseSecondary.play(popup.animateTranslationYBy(getHeight() - newHeight,
                    getResources().getInteger(R.integer.config_removeNotificationViewDuration)));
            collapseSecondary.play(LauncherAnimUtils.animateViewHeight(
                    this, getHeight(), newHeight));
            collapseSecondary.start();
        }
    }
}
