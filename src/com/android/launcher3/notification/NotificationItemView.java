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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Notification;
import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.popup.PopupItemView;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.Themes;

import java.util.List;

/**
 * A {@link FrameLayout} that contains a header, main view and a footer.
 * The main view contains the icon and text (title + subtext) of the first notification.
 * The footer contains: A list of just the icons of all the notifications past the first one.
 * @see NotificationFooterLayout
 */
public class NotificationItemView extends PopupItemView implements LogContainerProvider {

    private static final Rect sTempRect = new Rect();

    private TextView mHeaderText;
    private TextView mHeaderCount;
    private NotificationMainView mMainView;
    private NotificationFooterLayout mFooter;
    private SwipeDetector mSwipeDetector;
    private boolean mAnimatingNextIcon;
    private int mNotificationHeaderTextColor = Notification.COLOR_DEFAULT;

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
        mHeaderText = findViewById(R.id.notification_text);
        mHeaderCount = findViewById(R.id.notification_count);
        mMainView = findViewById(R.id.main_view);
        mFooter = findViewById(R.id.footer);

        mSwipeDetector = new SwipeDetector(getContext(), mMainView, SwipeDetector.HORIZONTAL);
        mSwipeDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, false);
        mMainView.setSwipeDetector(mSwipeDetector);
    }

    public NotificationMainView getMainView() {
        return mMainView;
    }

    /**
     * This method is used to calculate the height to remove when dismissing the last notification.
     * We subtract the height of the footer in this case since the footer should be gone or in the
     * process of being removed.
     * @return The height of the entire notification item, minus the footer if it still exists.
     */
    public int getHeightMinusFooter() {
        if (mFooter.getParent() == null) {
            return getHeight();
        }
        int excessFooterHeight = mFooter.getHeight() - getResources().getDimensionPixelSize(
                R.dimen.notification_empty_footer_height);
        return getHeight() - excessFooterHeight;
    }

    public Animator animateHeightRemoval(int heightToRemove, boolean shouldRemoveFromTop) {
        AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();

        Rect startRect = new Rect(mPillRect);
        Rect endRect = new Rect(mPillRect);
        if (shouldRemoveFromTop) {
            endRect.top += heightToRemove;
        } else {
            endRect.bottom -= heightToRemove;
        }
        anim.play(new RoundedRectRevealOutlineProvider(getBackgroundRadius(), getBackgroundRadius(),
                startRect, endRect, mRoundedCorners).createRevealAnimator(this, false));

        View bottomGutter = findViewById(R.id.gutter_bottom);
        if (bottomGutter != null && bottomGutter.getVisibility() == VISIBLE) {
            Animator translateGutter = ObjectAnimator.ofFloat(bottomGutter, TRANSLATION_Y,
                    -heightToRemove);
            translateGutter.addListener(new PropertyResetListener<>(TRANSLATION_Y, 0f));
            anim.play(translateGutter);
        }

        return anim;
    }

    public void updateHeader(int notificationCount, @Nullable IconPalette palette) {
        mHeaderCount.setText(notificationCount <= 1 ? "" : String.valueOf(notificationCount));
        if (palette != null) {
            if (mNotificationHeaderTextColor == Notification.COLOR_DEFAULT) {
                mNotificationHeaderTextColor =
                        IconPalette.resolveContrastColor(getContext(), palette.dominantColor,
                                Themes.getAttrColor(getContext(), R.attr.popupColorPrimary));
            }
            mHeaderText.setTextColor(mNotificationHeaderTextColor);
            mHeaderCount.setTextColor(mNotificationHeaderTextColor);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        mSwipeDetector.onTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mMainView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }
        return mSwipeDetector.onTouchEvent(ev) || super.onTouchEvent(ev);
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
                        mMainView.setVisibility(VISIBLE);
                    }
                    mAnimatingNextIcon = false;
                }
            });
        } else {
            mFooter.trimNotifications(notificationKeys);
        }
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        target.itemType = LauncherLogProto.ItemType.NOTIFICATION;
        targetParent.containerType = LauncherLogProto.ContainerType.DEEPSHORTCUTS;
    }
}
