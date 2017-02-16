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
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.popup.PopupItemView;
import com.android.launcher3.userevent.nano.LauncherLogProto;

import java.util.ArrayList;
import java.util.List;

import static com.android.launcher3.LauncherAnimUtils.animateViewHeight;

/**
 * A {@link FrameLayout} that contains a header, main view and a footer.
 * The main view contains the icon and text (title + subtext) of the first notification.
 * The footer contains: A list of just the icons of all the notifications past the first one.
 * @see NotificationFooterLayout
 */
public class NotificationItemView extends PopupItemView implements LogContainerProvider {

    private static final Rect sTempRect = new Rect();

    private TextView mHeader;
    private View mDivider;
    private NotificationMainView mMainView;
    private NotificationFooterLayout mFooter;
    private SwipeHelper mSwipeHelper;
    private boolean mAnimatingNextIcon;

    public NotificationItemView(Context context) {
        this(context, null, 0);
    }

    public NotificationItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeader = (TextView) findViewById(R.id.header);
        mDivider = findViewById(R.id.divider);
        mMainView = (NotificationMainView) findViewById(R.id.main_view);
        mFooter = (NotificationFooterLayout) findViewById(R.id.footer);
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, mMainView, getContext());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        return mSwipeHelper.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }
        return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    @Override
    protected ColorStateList getAttachedArrowColor() {
        // This NotificationView itself has a different color that is only
        // revealed when dismissing notifications.
        return mFooter.getBackgroundTintList();
    }

    public void applyNotificationInfos(final List<NotificationInfo> notificationInfos) {
        if (notificationInfos.isEmpty()) {
            return;
        }

        NotificationInfo mainNotification = notificationInfos.get(0);
        mMainView.applyNotificationInfo(mainNotification, mIconView);

        for (int i = 1; i < notificationInfos.size(); i++) {
            mFooter.addNotificationInfo(notificationInfos.get(i));
        }
        mFooter.commitNotificationInfos();
    }

    public void applyColors(IconPalette iconPalette) {
        setBackgroundTintList(ColorStateList.valueOf(iconPalette.secondaryColor));
        mHeader.setBackgroundTintList(ColorStateList.valueOf(iconPalette.backgroundColor));
        mHeader.setTextColor(ColorStateList.valueOf(iconPalette.textColor));
        mDivider.setBackgroundColor(iconPalette.secondaryColor);
        mMainView.applyColors(iconPalette);
        mFooter.applyColors(iconPalette);
    }

    public void trimNotifications(final List<String> notificationKeys) {
        boolean dismissedMainNotification = !notificationKeys.contains(
                mMainView.getNotificationInfo().notificationKey);
        if (dismissedMainNotification && !mAnimatingNextIcon) {
            // Animate the next icon into place as the new main notification.
            mAnimatingNextIcon = true;
            mMainView.setVisibility(INVISIBLE);
            mMainView.setTranslationX(0);
            mIconView.getGlobalVisibleRect(sTempRect);
            mFooter.animateFirstNotificationTo(sTempRect,
                    new NotificationFooterLayout.IconAnimationEndListener() {
                @Override
                public void onIconAnimationEnd(NotificationInfo newMainNotification) {
                    if (newMainNotification != null) {
                        mMainView.applyNotificationInfo(newMainNotification, mIconView, true);
                        // Remove the animated notification from the footer by calling trim
                        // TODO: Remove the notification in NotificationFooterLayout directly
                        // instead of relying on this hack.
                        List<String> footerNotificationKeys = new ArrayList<>(notificationKeys);
                        footerNotificationKeys.remove(newMainNotification.notificationKey);
                        mFooter.trimNotifications(footerNotificationKeys);
                        mMainView.setVisibility(VISIBLE);
                    }
                    mAnimatingNextIcon = false;
                }
            });
        } else {
            mFooter.trimNotifications(notificationKeys);
        }
    }

    public Animator createRemovalAnimation(int fullDuration) {
        AnimatorSet animation = new AnimatorSet();
        int mainHeight = mMainView.getMeasuredHeight();
        Animator removeMainView = animateViewHeight(mMainView, mainHeight, 0);
        removeMainView.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Remove the remaining views but take on their color instead of the darker one.
                setBackgroundTintList(mHeader.getBackgroundTintList());
                removeAllViews();
            }
        });
        Animator removeRest = LauncherAnimUtils.animateViewHeight(this, getHeight() - mainHeight, 0);
        removeMainView.setDuration(fullDuration / 2);
        removeRest.setDuration(fullDuration / 2);
        removeMainView.setInterpolator(new LinearInterpolator());
        removeRest.setInterpolator(new LinearInterpolator());
        animation.playSequentially(removeMainView, removeRest);
        return animation;
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        target.itemType = LauncherLogProto.ItemType.NOTIFICATION;
        targetParent.containerType = LauncherLogProto.ContainerType.DEEPSHORTCUTS;
    }
}
