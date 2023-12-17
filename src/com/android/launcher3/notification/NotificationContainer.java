/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.app.animation.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.touch.SingleAxisSwipeDetector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to manage the notification UI in a {@link PopupContainerWithArrow}.
 *
 * - Has two {@link NotificationMainView} that represent the top two notifications
 * - Handles dismissing a notification
 */
public class NotificationContainer extends FrameLayout implements SingleAxisSwipeDetector.Listener {

    private static final FloatProperty<NotificationContainer> DRAG_TRANSLATION_X =
            new FloatProperty<NotificationContainer>("notificationProgress") {
                @Override
                public void setValue(NotificationContainer view, float transX) {
                    view.setDragTranslationX(transX);
                }

                @Override
                public Float get(NotificationContainer view) {
                    return view.mDragTranslationX;
                }
            };

    private static final Rect sTempRect = new Rect();

    private final SingleAxisSwipeDetector mSwipeDetector;
    private final List<NotificationInfo> mNotificationInfos = new ArrayList<>();
    private boolean mIgnoreTouch = false;

    private final ObjectAnimator mContentTranslateAnimator;
    private float mDragTranslationX = 0;

    private final NotificationMainView mPrimaryView;
    private final NotificationMainView mSecondaryView;
    private PopupContainerWithArrow mPopupContainer;

    public NotificationContainer(Context context) {
        this(context, null, 0);
    }

    public NotificationContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSwipeDetector = new SingleAxisSwipeDetector(getContext(), this, HORIZONTAL);
        mSwipeDetector.setDetectableScrollConditions(SingleAxisSwipeDetector.DIRECTION_BOTH, false);
        mContentTranslateAnimator = ObjectAnimator.ofFloat(this, DRAG_TRANSLATION_X, 0);

        mPrimaryView = (NotificationMainView) View.inflate(getContext(),
                R.layout.notification_content, null);
        mSecondaryView = (NotificationMainView) View.inflate(getContext(),
                R.layout.notification_content, null);
        mSecondaryView.setAlpha(0);

        addView(mSecondaryView);
        addView(mPrimaryView);

    }

    public void setPopupView(PopupContainerWithArrow popupView) {
        mPopupContainer = popupView;
    }

    /**
     * Returns true if we should intercept the swipe.
     */
    public boolean onInterceptSwipeEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            sTempRect.set(getLeft(), getTop(), getRight(), getBottom());
            mIgnoreTouch = !sTempRect.contains((int) ev.getX(), (int) ev.getY());
            if (!mIgnoreTouch) {
                mPopupContainer.getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        if (mIgnoreTouch) {
            return false;
        }
        if (mPrimaryView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }

        mSwipeDetector.onTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    /**
     * Returns true when we should handle the swipe.
     */
    public boolean onSwipeEvent(MotionEvent ev) {
        if (mIgnoreTouch) {
            return false;
        }
        if (mPrimaryView.getNotificationInfo() == null) {
            // The notification hasn't been populated yet.
            return false;
        }
        return mSwipeDetector.onTouchEvent(ev);
    }

    /**
     * Applies the list of @param notificationInfos to this container.
     */
    public void applyNotificationInfos(final List<NotificationInfo> notificationInfos) {
        mNotificationInfos.clear();
        if (notificationInfos.isEmpty()) {
            mPrimaryView.applyNotificationInfo(null);
            mSecondaryView.applyNotificationInfo(null);
            return;
        }
        mNotificationInfos.addAll(notificationInfos);

        NotificationInfo mainNotification = notificationInfos.get(0);
        mPrimaryView.applyNotificationInfo(mainNotification);
        mSecondaryView.applyNotificationInfo(notificationInfos.size() > 1
                ? notificationInfos.get(1)
                : null);
    }

    /**
     * Trims the notifications.
     * @param notificationKeys List of all valid notification keys.
     */
    public void trimNotifications(final List<String> notificationKeys) {
        Iterator<NotificationInfo> iterator = mNotificationInfos.iterator();
        while (iterator.hasNext()) {
            if (!notificationKeys.contains(iterator.next().notificationKey)) {
                iterator.remove();
            }
        }

        NotificationInfo primaryInfo = mNotificationInfos.size() > 0
                ? mNotificationInfos.get(0)
                : null;
        NotificationInfo secondaryInfo = mNotificationInfos.size() > 1
                ? mNotificationInfos.get(1)
                : null;

        mPrimaryView.applyNotificationInfo(primaryInfo);
        mSecondaryView.applyNotificationInfo(secondaryInfo);

        mPrimaryView.onPrimaryDrag(0);
        mSecondaryView.onSecondaryDrag(0);
    }

    private void setDragTranslationX(float translationX) {
        mDragTranslationX = translationX;

        float progress = translationX / getWidth();
        mPrimaryView.onPrimaryDrag(progress);
        if (mSecondaryView.getNotificationInfo() == null) {
            mSecondaryView.setAlpha(0f);
        } else {
            mSecondaryView.onSecondaryDrag(progress);
        }
    }

    // SingleAxisSwipeDetector.Listener's
    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        mPopupContainer.showArrow(false);
    }

    @Override
    public boolean onDrag(float displacement) {
        if (!mPrimaryView.canChildBeDismissed()) {
            displacement = OverScroll.dampedScroll(displacement, getWidth());
        }

        float progress = displacement / getWidth();
        mPrimaryView.onPrimaryDrag(progress);
        if (mSecondaryView.getNotificationInfo() == null) {
            mSecondaryView.setAlpha(0f);
        } else {
            mSecondaryView.onSecondaryDrag(progress);
        }
        mContentTranslateAnimator.cancel();
        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        final boolean willExit;
        final float endTranslation;
        final float startTranslation = mPrimaryView.getTranslationX();
        final float width = getWidth();

        if (!mPrimaryView.canChildBeDismissed()) {
            willExit = false;
            endTranslation = 0;
        } else if (mSwipeDetector.isFling(velocity)) {
            willExit = true;
            endTranslation = velocity < 0 ? -width : width;
        } else if (Math.abs(startTranslation) > width / 2f) {
            willExit = true;
            endTranslation = (startTranslation < 0 ? -width : width);
        } else {
            willExit = false;
            endTranslation = 0;
        }

        long duration = BaseSwipeDetector.calculateDuration(velocity,
                (endTranslation - startTranslation) / width);

        mContentTranslateAnimator.removeAllListeners();
        mContentTranslateAnimator.setDuration(duration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
        mContentTranslateAnimator.setFloatValues(startTranslation, endTranslation);

        NotificationMainView current = mPrimaryView;
        mContentTranslateAnimator.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mSwipeDetector.finishedScrolling();
                if (willExit) {
                    current.onChildDismissed();
                }
                mPopupContainer.showArrow(true);
            }
        });
        mContentTranslateAnimator.start();
    }

    /**
     * Animates the background color to a new color.
     * @param color The color to change to.
     * @param animatorSetOut The AnimatorSet where we add the color animator to.
     */
    public void updateBackgroundColor(int color, AnimatorSet animatorSetOut) {
        mPrimaryView.updateBackgroundColor(color, animatorSetOut);
        mSecondaryView.updateBackgroundColor(color, animatorSetOut);
    }

    /**
     * Updates the header with a new @param notificationCount.
     */
    public void updateHeader(int notificationCount) {
        mPrimaryView.updateHeader(notificationCount);
        mSecondaryView.updateHeader(notificationCount - 1);
    }
}
