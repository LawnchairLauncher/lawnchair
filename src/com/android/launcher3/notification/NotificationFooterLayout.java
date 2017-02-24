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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.popup.PopupContainerWithArrow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link LinearLayout} that contains only icons of notifications.
 * If there are more than {@link #MAX_FOOTER_NOTIFICATIONS} icons, we add a "+x" overflow.
 */
public class NotificationFooterLayout extends LinearLayout {

    public interface IconAnimationEndListener {
        void onIconAnimationEnd(NotificationInfo animatedNotification);
    }

    private static final int MAX_FOOTER_NOTIFICATIONS = 4;

    private static final Rect sTempRect = new Rect();

    private final List<NotificationInfo> mNotifications = new ArrayList<>();
    private final List<NotificationInfo> mOverflowNotifications = new ArrayList<>();
    private final boolean mRtl;

    LinearLayout.LayoutParams mIconLayoutParams;
    private LinearLayout mIconRow;
    private final ColorDrawable mBackgroundColor;
    private int mTextColor;
    private TextView mOverflowView;

    public NotificationFooterLayout(Context context) {
        this(context, null, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mRtl = Utilities.isRtl(getResources());

        int size = getResources().getDimensionPixelSize(
                R.dimen.notification_footer_icon_size);
        int padding = getResources().getDimensionPixelSize(R.dimen.notification_padding);
        mIconLayoutParams = new LayoutParams(size, size);
        mIconLayoutParams.setMarginEnd(padding);
        mIconLayoutParams.gravity = Gravity.CENTER_VERTICAL;

        mBackgroundColor = new ColorDrawable();
        setBackground(mBackgroundColor);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconRow = (LinearLayout) findViewById(R.id.icon_row);
    }

    public void applyColors(IconPalette iconPalette) {
        mBackgroundColor.setColor(iconPalette.backgroundColor);
        findViewById(R.id.divider).setBackgroundColor(iconPalette.secondaryColor);
        mTextColor = iconPalette.textColor;
    }

    public int getBackgroundColor() {
        return mBackgroundColor.getColor();
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

        for (int i = 0; i < mNotifications.size(); i++) {
            NotificationInfo info = mNotifications.get(i);
            addNotificationIconForInfo(info, false /* fromOverflow */);
        }

        if (!mOverflowNotifications.isEmpty()) {
            mOverflowView = new TextView(getContext());
            mOverflowView.setTextColor(mTextColor);
            updateOverflowText();
            mIconRow.addView(mOverflowView, 0, mIconLayoutParams);
        }
    }

    private void addNotificationIconForInfo(NotificationInfo info, boolean fromOverflow) {
        View icon = new View(getContext());
        icon.setBackground(info.getIconForBackground(getContext(), getBackgroundColor()));
        icon.setOnClickListener(info);
        int addIndex = 0;
        if (fromOverflow) {
            // Add the notification before the overflow view.
            addIndex = 1;
            icon.setAlpha(0);
            icon.animate().alpha(1);
        }
        icon.setTag(info);
        mIconRow.addView(icon, addIndex, mIconLayoutParams);
    }

    private void updateOverflowText() {
        mOverflowView.setText(getResources().getString(R.string.deep_notifications_overflow,
                mOverflowNotifications.size()));
    }

    public void animateFirstNotificationTo(Rect toBounds,
            final IconAnimationEndListener callback) {
        AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final View firstNotification = mIconRow.getChildAt(mIconRow.getChildCount() - 1);

        Rect fromBounds = sTempRect;
        firstNotification.getGlobalVisibleRect(fromBounds);
        float scale = (float) toBounds.height() / fromBounds.height();
        Animator moveAndScaleIcon = LauncherAnimUtils.ofPropertyValuesHolder(firstNotification,
                new PropertyListBuilder().scale(scale).translationY(toBounds.top - fromBounds.top
                        + (fromBounds.height() * scale - fromBounds.height()) / 2).build());
        moveAndScaleIcon.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                callback.onIconAnimationEnd((NotificationInfo) firstNotification.getTag());
                removeViewFromIconRow(firstNotification);
            }
        });
        animation.play(moveAndScaleIcon);

        // Shift all notifications (not the overflow) over to fill the gap.
        int gapWidth = mIconLayoutParams.width + mIconLayoutParams.getMarginEnd();
        if (mRtl) {
            gapWidth = -gapWidth;
        }
        int numIcons = mIconRow.getChildCount() - 1;
        // We have to set the translation X to 0 when the new main notification
        // is removed from the footer.
        PropertyResetListener<View, Float> propertyResetListener
                = new PropertyResetListener<>(TRANSLATION_X, 0f);
        for (int i = mOverflowNotifications.isEmpty() ? 0 : 1; i < numIcons; i++) {
            final View child = mIconRow.getChildAt(i);
            Animator shiftChild = ObjectAnimator.ofFloat(child, TRANSLATION_X, gapWidth);
            shiftChild.addListener(propertyResetListener);
            animation.play(shiftChild);
        }
        animation.start();
    }

    private void removeViewFromIconRow(View child) {
        mIconRow.removeView(child);
        mNotifications.remove((NotificationInfo) child.getTag());
        if (!mOverflowNotifications.isEmpty()) {
            NotificationInfo notification = mOverflowNotifications.remove(0);
            mNotifications.add(notification);
            addNotificationIconForInfo(notification, true /* fromOverflow */);
        }
        if (mOverflowView != null) {
            if (mOverflowNotifications.isEmpty()) {
                mIconRow.removeView(mOverflowView);
                mOverflowView = null;
            } else {
                updateOverflowText();
            }
        }
        if (mIconRow.getChildCount() == 0) {
            // There are no more icons in the footer, so hide it.
            PopupContainerWithArrow popup = PopupContainerWithArrow.getOpen(
                    Launcher.getLauncher(getContext()));
            Animator collapseFooter = popup.reduceNotificationViewHeight(getHeight(),
                    getResources().getInteger(R.integer.config_removeNotificationViewDuration));
            collapseFooter.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ((ViewGroup) getParent()).removeView(NotificationFooterLayout.this);
                }
            });
            collapseFooter.start();
        }
    }

    public void trimNotifications(List<String> notifications) {
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
                NotificationInfo childInfo = (NotificationInfo) child.getTag();
                if (!notifications.contains(childInfo.notificationKey)) {
                    removeViewFromIconRow(child);
                }
            }
        }
    }
}
