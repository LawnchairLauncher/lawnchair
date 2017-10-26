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

package ch.deletescape.lawnchair.notification;

import android.animation.Animator;
import android.app.Notification;
import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.anim.PillHeightRevealOutlineProvider;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.popup.PopupItemView;

/**
 * A {@link FrameLayout} that contains a header, main view and a footer.
 * The main view contains the icon and text (title + subtext) of the first notification.
 * The footer contains: A list of just the icons of all the notifications past the first one.
 * @see NotificationFooterLayout
 */
public class NotificationItemView extends PopupItemView {

    private static final Rect sTempRect = new Rect();

    private TextView mHeaderText;
    private TextView mHeaderCount;
    private View mDivider;
    private FrameLayout mHeaderView;
    private NotificationMainView mMainView;
    private NotificationFooterLayout mFooter;
    private SwipeHelper mSwipeHelper;
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
        mHeaderView = findViewById(R.id.header);
        mMainView = findViewById(R.id.main_view);
        mDivider = findViewById(R.id.divider);
        mFooter = findViewById(R.id.footer);
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, mMainView, getContext());
        mSwipeHelper.setDisableHardwareLayers(true);
    }

    public NotificationMainView getMainView() {
        return mMainView;
    }

    public int getHeightMinusFooter() {
        int footerHeight = mFooter.getParent() == null ? 0 : mFooter.getHeight();
        return getHeight() - footerHeight;
    }

    public Animator animateHeightRemoval(int heightToRemove) {
        final int newHeight = getHeight() - heightToRemove;
        return new PillHeightRevealOutlineProvider(mPillRect,
                getBackgroundRadius(), newHeight).createRevealAnimator(this, true /* isReversed */);
    }

    public void updateHeader(int notificationCount, @Nullable IconPalette palette) {
        mHeaderCount.setText(notificationCount <= 1 ? "" : String.valueOf(notificationCount));
        if (palette != null) {
            if (mNotificationHeaderTextColor == Notification.COLOR_DEFAULT) {
                mNotificationHeaderTextColor =
                        IconPalette.resolveContrastColor(getContext(), palette.dominantColor,
                                getResources().getColor(R.color.popup_header_background_color));
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

    public void applyNotificationInfos(final List<NotificationInfo> notificationInfos) {
        if (notificationInfos.isEmpty()) {
            return;
        }

        NotificationInfo mainNotification = notificationInfos.get(0);
        mMainView.applyNotificationInfo(mainNotification, mIconView);

        mDivider.setVisibility(notificationInfos.size() > 1 ? VISIBLE : INVISIBLE);
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

    public void showSectionDivider(boolean isAboveIcon) {
        mHeaderView.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.system_shortcut_header_height);
        findViewById(isAboveIcon ? R.id.top_divider : R.id.bottom_divider).setVisibility(VISIBLE);
    }

    @Override
    public int getArrowColor(boolean isArrowAttachedToBottom) {
        Context context = getContext();
        if (isArrowAttachedToBottom) {
            return Utilities.resolveAttributeData(context, R.attr.popupColorPrimary);
        } else {
            return Utilities.resolveAttributeData(context, R.attr.appPopupHeaderBgColor);
        }
    }
}