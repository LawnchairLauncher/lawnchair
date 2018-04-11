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

import android.app.Notification;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.util.Themes;

import java.util.List;

import static com.android.launcher3.touch.SwipeDetector.HORIZONTAL;

/**
 * Utility class to manage notification UI
 */
public class NotificationItemView {

    private static final Rect sTempRect = new Rect();

    private final Context mContext;
    private final PopupContainerWithArrow mContainer;

    private final TextView mHeaderText;
    private final TextView mHeaderCount;
    private final NotificationMainView mMainView;
    private final NotificationFooterLayout mFooter;
    private final SwipeDetector mSwipeDetector;
    private final View mIconView;

    private final View mHeader;
    private final View mDivider;

    private View mGutter;

    private boolean mIgnoreTouch = false;
    private boolean mAnimatingNextIcon;
    private int mNotificationHeaderTextColor = Notification.COLOR_DEFAULT;

    public NotificationItemView(PopupContainerWithArrow container) {
        mContainer = container;
        mContext = container.getContext();

        mHeaderText = container.findViewById(R.id.notification_text);
        mHeaderCount = container.findViewById(R.id.notification_count);
        mMainView = container.findViewById(R.id.main_view);
        mFooter = container.findViewById(R.id.footer);
        mIconView = container.findViewById(R.id.popup_item_icon);

        mHeader = container.findViewById(R.id.header);
        mDivider = container.findViewById(R.id.divider);

        mSwipeDetector = new SwipeDetector(mContext, mMainView, HORIZONTAL);
        mSwipeDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, false);
        mMainView.setSwipeDetector(mSwipeDetector);
        mFooter.setContainer(this);
    }

    public void addGutter() {
        if (mGutter == null) {
            mGutter = mContainer.inflateAndAdd(R.layout.notification_gutter, mContainer);
        }
    }

    public void removeFooter() {
        if (mContainer.indexOfChild(mFooter) >= 0) {
            mContainer.removeView(mFooter);
            mContainer.removeView(mDivider);
        }
    }

    public void inverseGutterMargin() {
        MarginLayoutParams lp = (MarginLayoutParams) mGutter.getLayoutParams();
        int top = lp.topMargin;
        lp.topMargin = lp.bottomMargin;
        lp.bottomMargin = top;
    }

    public void removeAllViews() {
        mContainer.removeView(mMainView);
        mContainer.removeView(mHeader);

        if (mContainer.indexOfChild(mFooter) >= 0) {
            mContainer.removeView(mFooter);
            mContainer.removeView(mDivider);
        }

        if (mGutter != null) {
            mContainer.removeView(mGutter);
        }
    }

    public void updateHeader(int notificationCount, int iconColor) {
        mHeaderCount.setText(notificationCount <= 1 ? "" : String.valueOf(notificationCount));
        if (Color.alpha(iconColor) > 0) {
            if (mNotificationHeaderTextColor == Notification.COLOR_DEFAULT) {
                mNotificationHeaderTextColor =
                        IconPalette.resolveContrastColor(mContext, iconColor,
                                Themes.getAttrColor(mContext, R.attr.popupColorPrimary));
            }
            mHeaderText.setTextColor(mNotificationHeaderTextColor);
            mHeaderCount.setTextColor(mNotificationHeaderTextColor);
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            sTempRect.set(mMainView.getLeft(), mMainView.getTop(),
                    mMainView.getRight(), mMainView.getBottom());
            mIgnoreTouch = !sTempRect.contains((int) ev.getX(), (int) ev.getY());
            if (!mIgnoreTouch) {
                mContainer.getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        if (mIgnoreTouch) {
            return false;
        }
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }

        mSwipeDetector.onTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mIgnoreTouch) {
            return false;
        }
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }
        return mSwipeDetector.onTouchEvent(ev);
    }

    public void applyNotificationInfos(final List<NotificationInfo> notificationInfos) {
        if (notificationInfos.isEmpty()) {
            return;
        }

        NotificationInfo mainNotification = notificationInfos.get(0);
        mMainView.applyNotificationInfo(mainNotification, false);

        for (int i = 1; i < notificationInfos.size(); i++) {
            mFooter.addNotificationInfo(notificationInfos.get(i));
        }
        mFooter.commitNotificationInfos();
    }

    public void trimNotifications(final List<String> notificationKeys) {
        boolean dismissedMainNotification = !notificationKeys.contains(
                mMainView.getNotificationInfo().notificationKey);
        if (dismissedMainNotification && !mAnimatingNextIcon) {
            // Animate the next icon into place as the new main notification.
            mAnimatingNextIcon = true;
            mMainView.setContentVisibility(View.INVISIBLE);
            mMainView.setContentTranslation(0);
            mIconView.getGlobalVisibleRect(sTempRect);
            mFooter.animateFirstNotificationTo(sTempRect, (newMainNotification) -> {
                if (newMainNotification != null) {
                    mMainView.applyNotificationInfo(newMainNotification, true);
                    mMainView.setContentVisibility(View.VISIBLE);
                }
                mAnimatingNextIcon = false;
            });
        } else {
            mFooter.trimNotifications(notificationKeys);
        }
    }
}
