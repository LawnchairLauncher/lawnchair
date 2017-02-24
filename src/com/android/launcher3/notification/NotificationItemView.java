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
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.popup.PopupItemView;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.anim.PillHeightRevealOutlineProvider;

import java.util.List;

/**
 * A {@link FrameLayout} that contains a header, main view and a footer.
 * The main view contains the icon and text (title + subtext) of the first notification.
 * The footer contains: A list of just the icons of all the notifications past the first one.
 * @see NotificationFooterLayout
 */
public class NotificationItemView extends PopupItemView implements LogContainerProvider {

    private static final Rect sTempRect = new Rect();

    private final Paint mBackgroundClipPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG |
            Paint.FILTER_BITMAP_FLAG);

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
        mDivider = findViewById(R.id.divider);
        mMainView = (NotificationMainView) findViewById(R.id.main_view);
        mFooter = (NotificationFooterLayout) findViewById(R.id.footer);
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, mMainView, getContext());
        mSwipeHelper.setDisableHardwareLayers(true);
    }

    private void initializeBackgroundClipping(boolean force) {
        if (force || mBackgroundClipPaint.getShader() == null) {
            mBackgroundClipPaint.setXfermode(null);
            mBackgroundClipPaint.setShader(null);
            Bitmap backgroundBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas();
            canvas.setBitmap(backgroundBitmap);
            float roundRectRadius = getResources().getDimensionPixelSize(
                    R.dimen.bg_round_rect_radius);
            canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(),
                    roundRectRadius, roundRectRadius, mBackgroundClipPaint);
            Shader backgroundClipShader = new BitmapShader(backgroundBitmap,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mBackgroundClipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            mBackgroundClipPaint.setShader(backgroundClipShader);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        initializeBackgroundClipping(false /* force */);
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null,
                Canvas.HAS_ALPHA_LAYER_SAVE_FLAG | Canvas.CLIP_TO_LAYER_SAVE_FLAG);
        super.dispatchDraw(canvas);
        canvas.drawPaint(mBackgroundClipPaint);
        canvas.restoreToCount(saveCount);
    }

    public Animator animateHeightRemoval(int heightToRemove) {
        final int newHeight = getHeight() - heightToRemove;
        Animator heightAnimator = new PillHeightRevealOutlineProvider(mPillRect,
                getBackgroundRadius(), newHeight).createRevealAnimator(this, true /* isReversed */);
        heightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (newHeight > 0) {
                    measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY));
                    initializeBackgroundClipping(true /* force */);
                    invalidate();
                } else {
                    ((ViewGroup) getParent()).removeView(NotificationItemView.this);
                }
            }
        });
        return heightAnimator;
    }

    @Override
    protected float getBackgroundRadius() {
        return getResources().getDimensionPixelSize(R.dimen.bg_round_rect_radius);
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
        return ColorStateList.valueOf(mFooter.getBackgroundColor());
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
