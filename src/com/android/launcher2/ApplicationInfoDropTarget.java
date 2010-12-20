/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import com.android.launcher.R;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.Animator.AnimatorListener;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Implements a DropTarget which allows applications to be dropped on it,
 * in order to launch the application info for that app.
 */
public class ApplicationInfoDropTarget extends IconDropTarget {
    private static final int sFadeInAnimationDuration = 200;
    private static final int sFadeOutAnimationDuration = 100;

    private AnimatorSet mFadeAnimator;
    private ObjectAnimator mHandleFadeAnimator;
    private boolean mHandleWasVisibleOnDragStart;

    public ApplicationInfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ApplicationInfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Set the hover paint colour
        int colour = getContext().getResources().getColor(R.color.app_info_filter);
        mHoverPaint.setColorFilter(new PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_ATOP));

        // For the application info drop target, we just ignore the left padding since we don't want
        // to overlap with the delete zone padding
        int tb = getResources().getDimensionPixelSize(R.dimen.delete_zone_vertical_drag_padding);
        int lr = getResources().getDimensionPixelSize(R.dimen.delete_zone_horizontal_drag_padding);
        setDragPadding(tb, lr, tb, 0);
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

        // acceptDrop is called just before onDrop. We do the work here, rather than
        // in onDrop, because it allows us to reject the drop (by returning false)
        // so that the object being dragged isn't removed from the home screen.
        if (getVisibility() != VISIBLE) return false;

        ComponentName componentName = null;
        if (dragInfo instanceof ApplicationInfo) {
            componentName = ((ApplicationInfo)dragInfo).componentName;
        } else if (dragInfo instanceof ShortcutInfo) {
            componentName = ((ShortcutInfo)dragInfo).intent.getComponent();
        }
        mLauncher.startApplicationDetailsActivity(componentName);
        return false;
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (!mDragAndDropEnabled) return;
        dragView.setPaint(mHoverPaint);
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (!mDragAndDropEnabled) return;
        dragView.setPaint(null);
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        if (info != null && mDragAndDropEnabled) {
            final int itemType = ((ItemInfo)info).itemType;
            mActive = (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION);
            if (mActive) {
                // Fade in this icon
                if (mFadeAnimator != null) mFadeAnimator.cancel();
                mFadeAnimator = new AnimatorSet();
                Animator infoButtonAnimator = ObjectAnimator.ofFloat(this, "alpha", 0.0f, 1.0f);
                infoButtonAnimator.setDuration(sFadeInAnimationDuration);

                if (mHandle == mLauncher.findViewById(R.id.configure_button)) {
                    final View divider = mLauncher.findViewById(R.id.divider_during_drag);
                    divider.setVisibility(VISIBLE);
                    Animator dividerAnimator = ObjectAnimator.ofFloat(divider, "alpha", 1.0f);
                    dividerAnimator.setDuration(sFadeInAnimationDuration);
                    mFadeAnimator.play(infoButtonAnimator).with(dividerAnimator);
                } else {
                    mFadeAnimator.play(infoButtonAnimator);
                }
                mFadeAnimator.start();
                setVisibility(VISIBLE);

                // Fade out the handle
                if (mHandle != null) {
                    mHandleWasVisibleOnDragStart = mHandle.getVisibility() == VISIBLE;
                    if (mHandleFadeAnimator != null) mHandleFadeAnimator.cancel();
                    mHandleFadeAnimator = ObjectAnimator.ofFloat(mHandle, "alpha", 0.0f);
                    mHandleFadeAnimator.setDuration(sFadeOutAnimationDuration);
                    mHandleFadeAnimator.addListener(new AnimatorListener() {
                        public void onAnimationStart(Animator animation) {}
                        public void onAnimationRepeat(Animator animation) {}
                        public void onAnimationEnd(Animator animation) {
                            onEndOrCancel();
                        }
                        public void onAnimationCancel(Animator animation) {
                            onEndOrCancel();
                        }
                        private void onEndOrCancel() {
                            mHandle.setVisibility(INVISIBLE);
                            mHandleFadeAnimator = null;
                        }
                    });
                    mHandleFadeAnimator.start();
                }
            }
        }
    }

    public void onDragEnd() {
        if (!mDragAndDropEnabled) return;
        if (mActive) mActive = false;

        // Fade out this icon
        if (mFadeAnimator != null) mFadeAnimator.cancel();
        mFadeAnimator = new AnimatorSet();
        Animator infoButtonAnimator = ObjectAnimator.ofFloat(this, "alpha", 0.0f);
        infoButtonAnimator.setDuration(sFadeOutAnimationDuration);
        final View divider = mLauncher.findViewById(R.id.divider_during_drag);
        divider.setVisibility(VISIBLE);
        Animator dividerAnimator = ObjectAnimator.ofFloat(divider, "alpha", 0.0f);
        mFadeAnimator.addListener(new AnimatorListener() {
            public void onAnimationStart(Animator animation) {}
            public void onAnimationRepeat(Animator animation) {}
            public void onAnimationEnd(Animator animation) {
                onEndOrCancel();
            }
            public void onAnimationCancel(Animator animation) {
                onEndOrCancel();
            }
            private void onEndOrCancel() {
                setVisibility(GONE);
                divider.setVisibility(GONE);
                mFadeAnimator = null;
            }
        });
        mFadeAnimator.play(infoButtonAnimator).with(dividerAnimator);
        mFadeAnimator.start();

        // Fade in the handle
        if (mHandle != null && mHandleWasVisibleOnDragStart) {
            if (mHandleFadeAnimator != null) mHandleFadeAnimator.cancel();
            mHandleFadeAnimator = ObjectAnimator.ofFloat(mHandle, "alpha", 1.0f);
            mHandleFadeAnimator.setDuration(sFadeInAnimationDuration);
            mHandleFadeAnimator.start();
            mHandle.setVisibility(VISIBLE);
        }
    }
}
