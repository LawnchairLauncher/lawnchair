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
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.popup.PopupContainerWithArrow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link FrameLayout} that contains only icons of notifications.
 * If there are more than {@link #MAX_FOOTER_NOTIFICATIONS} icons, we add a "..." overflow.
 */
public class NotificationFooterLayout extends FrameLayout {

    public interface IconAnimationEndListener {
        void onIconAnimationEnd(NotificationInfo animatedNotification);
    }

    private static final int MAX_FOOTER_NOTIFICATIONS = 5;

    private static final Rect sTempRect = new Rect();

    private final List<NotificationInfo> mNotifications = new ArrayList<>();
    private final List<NotificationInfo> mOverflowNotifications = new ArrayList<>();
    private final boolean mRtl;

    FrameLayout.LayoutParams mIconLayoutParams;
    private View mOverflowEllipsis;
    private LinearLayout mIconRow;
    private int mBackgroundColor;

    public NotificationFooterLayout(Context context) {
        this(context, null, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationFooterLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = getResources();
        mRtl = Utilities.isRtl(res);

        int iconSize = res.getDimensionPixelSize(R.dimen.notification_footer_icon_size);
        mIconLayoutParams = new LayoutParams(iconSize, iconSize);
        mIconLayoutParams.gravity = Gravity.CENTER_VERTICAL;
        // Compute margin start for each icon such that the icons between the first one
        // and the ellipsis are evenly spaced out.
        int paddingEnd = res.getDimensionPixelSize(R.dimen.notification_footer_icon_row_padding);
        int ellipsisSpace = res.getDimensionPixelSize(R.dimen.horizontal_ellipsis_offset)
                + res.getDimensionPixelSize(R.dimen.horizontal_ellipsis_size);
        int footerWidth = res.getDimensionPixelSize(R.dimen.bg_popup_item_width);
        int availableIconRowSpace = footerWidth - paddingEnd - ellipsisSpace
                - iconSize * MAX_FOOTER_NOTIFICATIONS;
        mIconLayoutParams.setMarginStart(availableIconRowSpace / MAX_FOOTER_NOTIFICATIONS);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mOverflowEllipsis = findViewById(R.id.overflow);
        mIconRow = (LinearLayout) findViewById(R.id.icon_row);
        mBackgroundColor = ((ColorDrawable) getBackground()).getColor();
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
            addNotificationIconForInfo(info);
        }
        updateOverflowEllipsisVisibility();
    }

    private void updateOverflowEllipsisVisibility() {
        mOverflowEllipsis.setVisibility(mOverflowNotifications.isEmpty() ? GONE : VISIBLE);
    }

    /**
     * Creates an icon for the given NotificationInfo, and adds it to the icon row.
     * @return the icon view that was added
     */
    private View addNotificationIconForInfo(NotificationInfo info) {
        View icon = new View(getContext());
        icon.setBackground(info.getIconForBackground(getContext(), mBackgroundColor));
        icon.setOnClickListener(info);
        icon.setTag(info);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mIconRow.addView(icon, 0, mIconLayoutParams);
        return icon;
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
        int gapWidth = mIconLayoutParams.width + mIconLayoutParams.getMarginStart();
        if (mRtl) {
            gapWidth = -gapWidth;
        }
        if (!mOverflowNotifications.isEmpty()) {
            NotificationInfo notification = mOverflowNotifications.remove(0);
            mNotifications.add(notification);
            View iconFromOverflow = addNotificationIconForInfo(notification);
            animation.play(ObjectAnimator.ofFloat(iconFromOverflow, ALPHA, 0, 1));
        }
        int numIcons = mIconRow.getChildCount() - 1; // All children besides the one leaving.
        // We have to reset the translation X to 0 when the new main notification
        // is removed from the footer.
        PropertyResetListener<View, Float> propertyResetListener
                = new PropertyResetListener<>(TRANSLATION_X, 0f);
        for (int i = 0; i < numIcons; i++) {
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
        updateOverflowEllipsisVisibility();
        if (mIconRow.getChildCount() == 0) {
            // There are no more icons in the footer, so hide it.
            PopupContainerWithArrow popup = PopupContainerWithArrow.getOpen(
                    Launcher.getLauncher(getContext()));
            if (popup != null) {
                final int newHeight = getResources().getDimensionPixelSize(
                        R.dimen.notification_empty_footer_height);
                Animator collapseFooter = popup.reduceNotificationViewHeight(getHeight() - newHeight,
                        getResources().getInteger(R.integer.config_removeNotificationViewDuration));
                collapseFooter.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ((ViewGroup) getParent()).findViewById(R.id.divider).setVisibility(GONE);
                        // Keep view around because gutter is aligned to it, but remove height to
                        // both hide the view and keep calculations correct for last dismissal.
                        getLayoutParams().height = newHeight;
                        requestLayout();
                    }
                });
                collapseFooter.start();
            }
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
        for (int i = mIconRow.getChildCount() - 1; i >= 0; i--) {
            View child = mIconRow.getChildAt(i);
            NotificationInfo childInfo = (NotificationInfo) child.getTag();
            if (!notifications.contains(childInfo.notificationKey)) {
                removeViewFromIconRow(child);
            }
        }
    }
}
