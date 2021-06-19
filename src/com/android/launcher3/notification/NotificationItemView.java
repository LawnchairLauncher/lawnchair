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

import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.Themes;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to manage notification UI
 */
public class NotificationItemView {

    private static final Rect sTempRect = new Rect();

    private final Context mContext;
    private final PopupContainerWithArrow mPopupContainer;
    private final ViewGroup mRootView;

    private final TextView mHeaderCount;
    private final NotificationMainView mMainView;

    private final View mHeader;

    private View mGutter;

    private boolean mIgnoreTouch = false;
    private List<NotificationInfo> mNotificationInfos = new ArrayList<>();

    public NotificationItemView(PopupContainerWithArrow container, ViewGroup rootView) {
        mPopupContainer = container;
        mRootView = rootView;
        mContext = container.getContext();

        mHeaderCount = container.findViewById(R.id.notification_count);
        mMainView = container.findViewById(R.id.main_view);

        mHeader = container.findViewById(R.id.header);

        float radius = Themes.getDialogCornerRadius(mContext);
        rootView.setClipToOutline(true);
        rootView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    /**
     * Animates the background color to a new color.
     * @param color The color to change to.
     * @param animatorSetOut The AnimatorSet where we add the color animator to.
     */
    public void updateBackgroundColor(int color, AnimatorSet animatorSetOut) {
        mMainView.updateBackgroundColor(color, animatorSetOut);
    }

    public void addGutter() {
        if (mGutter == null) {
            mGutter = mPopupContainer.inflateAndAdd(R.layout.notification_gutter, mRootView);
        }
    }

    public void inverseGutterMargin() {
        MarginLayoutParams lp = (MarginLayoutParams) mGutter.getLayoutParams();
        int top = lp.topMargin;
        lp.topMargin = lp.bottomMargin;
        lp.bottomMargin = top;
    }

    public void removeAllViews() {
        mRootView.removeView(mMainView);
        mRootView.removeView(mHeader);
        if (mGutter != null) {
            mRootView.removeView(mGutter);
        }
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

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            sTempRect.set(mRootView.getLeft(), mRootView.getTop(),
                    mRootView.getRight(), mRootView.getBottom());
            mIgnoreTouch = !sTempRect.contains((int) ev.getX(), (int) ev.getY());
            if (!mIgnoreTouch) {
                mPopupContainer.getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        if (mIgnoreTouch) {
            return false;
        }
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }

        return false;
    }

    public void applyNotificationInfos(final List<NotificationInfo> notificationInfos) {
        mNotificationInfos.clear();
        if (notificationInfos.isEmpty()) {
            return;
        }
        mNotificationInfos.addAll(notificationInfos);

        NotificationInfo mainNotification = notificationInfos.get(0);
        mMainView.applyNotificationInfo(mainNotification, false);
    }

    public void trimNotifications(final List<String> notificationKeys) {
        NotificationInfo currentMainNotificationInfo = mMainView.getNotificationInfo();
        boolean shouldUpdateMainNotification = !notificationKeys.contains(
                currentMainNotificationInfo.notificationKey);

        if (shouldUpdateMainNotification) {
            int size = notificationKeys.size();
            NotificationInfo nextNotification = null;
            // We get the latest notification by finding the notification after the one that was
            // just dismissed.
            for (int i = 0; i < size; ++i) {
                if (currentMainNotificationInfo == mNotificationInfos.get(i) && i + 1 < size) {
                    nextNotification = mNotificationInfos.get(i + 1);
                    break;
                }
            }
            if (nextNotification != null) {
                mMainView.applyNotificationInfo(nextNotification, true);
            }
        }
    }
}
