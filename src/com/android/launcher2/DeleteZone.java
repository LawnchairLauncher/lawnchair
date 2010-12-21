/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;

import com.android.launcher.R;

public class DeleteZone extends IconDropTarget {
    private static final int ORIENTATION_HORIZONTAL = 1;
    private static final int TRANSITION_DURATION = 250;
    private static final int ANIMATION_DURATION = 200;
    private static final int XLARGE_TRANSITION_DURATION = 150;
    private static final int XLARGE_ANIMATION_DURATION = 200;

    private AnimationSet mInAnimation;
    private AnimationSet mOutAnimation;
    private Animation mHandleInAnimation;
    private Animation mHandleOutAnimation;

    private int mOrientation;
    private DragController mDragController;

    private final RectF mRegionF = new RectF();
    private final Rect mRegion = new Rect();
    private TransitionDrawable mTransition;

    public DeleteZone(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteZone(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final int srcColor = context.getResources().getColor(R.color.delete_color_filter);
        mHoverPaint.setColorFilter(new PorterDuffColorFilter(srcColor, PorterDuff.Mode.SRC_ATOP));

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DeleteZone, defStyle, 0);
        mOrientation = a.getInt(R.styleable.DeleteZone_direction, ORIENTATION_HORIZONTAL);
        a.recycle();

        int tb = getResources().getDimensionPixelSize(R.dimen.delete_zone_vertical_drag_padding);
        int lr = getResources().getDimensionPixelSize(R.dimen.delete_zone_horizontal_drag_padding);
        setDragPadding(tb, lr, tb, lr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTransition = (TransitionDrawable) getDrawable();
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return true;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (!mDragAndDropEnabled) return;

        final ItemInfo item = (ItemInfo) dragInfo;

        // On x-large screens, you can uninstall an app by dragging from all apps
        if (item instanceof ApplicationInfo && LauncherApplication.isScreenXLarge()) {
            mLauncher.startApplicationUninstallActivity((ApplicationInfo) item);
        }

        if (item.container == -1) return;

        if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            if (item instanceof LauncherAppWidgetInfo) {
                mLauncher.removeAppWidget((LauncherAppWidgetInfo) item);
            }
        } else if (source instanceof UserFolder) {
            final UserFolder userFolder = (UserFolder) source;
            final UserFolderInfo userFolderInfo = (UserFolderInfo) userFolder.getInfo();
            // Item must be a ShortcutInfo otherwise it couldn't have been in the folder
            // in the first place.
            userFolderInfo.remove((ShortcutInfo)item);
        }

        if (item instanceof UserFolderInfo) {
            final UserFolderInfo userFolderInfo = (UserFolderInfo)item;
            LauncherModel.deleteUserFolderContentsFromDatabase(mLauncher, userFolderInfo);
            mLauncher.removeFolder(userFolderInfo);
        } else if (item instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo launcherAppWidgetInfo = (LauncherAppWidgetInfo) item;
            final LauncherAppWidgetHost appWidgetHost = mLauncher.getAppWidgetHost();
            if (appWidgetHost != null) {
                final int appWidgetId = launcherAppWidgetInfo.appWidgetId;
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        appWidgetHost.deleteAppWidgetId(launcherAppWidgetInfo.appWidgetId);
                    }
                }.start();
            }
        }

        LauncherModel.deleteItemFromDatabase(mLauncher, item);
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (mDragAndDropEnabled) {
            mTransition.reverseTransition(getTransitionAnimationDuration());
            super.onDragEnter(source, x, y, xOffset, yOffset, dragView, dragInfo);
        }
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (mDragAndDropEnabled) {
            mTransition.reverseTransition(getTransitionAnimationDuration());
            super.onDragExit(source, x, y, xOffset, yOffset, dragView, dragInfo);
        }
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        final ItemInfo item = (ItemInfo) info;
        if (item != null && mDragAndDropEnabled) {
            mActive = true;
            getHitRect(mRegion);
            mRegionF.set(mRegion);

            if (LauncherApplication.isScreenXLarge()) {
                // This region will be a "dead zone" for scrolling; make it extend to the edge of
                // the screen so users don't accidentally trigger a scroll while deleting items
                mRegionF.top = mLauncher.getWorkspace().getTop();
                mRegionF.right = mLauncher.getWorkspace().getRight();
            }

            mDragController.setDeleteRegion(mRegionF);

            // Make sure the icon is set to the default drawable, not the hover drawable
            mTransition.resetTransition();

            createAnimations();
            startAnimation(mInAnimation);
            if (mOverlappingViews != null) {
                for (View view : mOverlappingViews) {
                    view.startAnimation(mHandleOutAnimation);
                }
            }
            setVisibility(VISIBLE);
        }
    }

    public void onDragEnd() {
        if (mActive && mDragAndDropEnabled) {
            mActive = false;
            mDragController.setDeleteRegion(null);

            if (mOutAnimation != null) startAnimation(mOutAnimation);
            if (mHandleInAnimation != null && mOverlappingViews != null) {
                for (View view : mOverlappingViews) {
                    view.startAnimation(mHandleInAnimation);
                }
            }
            setVisibility(GONE);
        }
    }

    private void createAnimations() {
        int duration = getAnimationDuration();
        if (mHandleInAnimation == null) {
            mHandleInAnimation = new AlphaAnimation(0.0f, 1.0f);
            mHandleInAnimation.setDuration(duration);
        }

        if (mInAnimation == null) {
            mInAnimation = new FastAnimationSet();
            if (!LauncherApplication.isScreenXLarge()) {
                final AnimationSet animationSet = mInAnimation;
                animationSet.setInterpolator(new AccelerateInterpolator());
                animationSet.addAnimation(new AlphaAnimation(0.0f, 1.0f));
                if (mOrientation == ORIENTATION_HORIZONTAL) {
                    animationSet.addAnimation(new TranslateAnimation(Animation.ABSOLUTE, 0.0f,
                            Animation.ABSOLUTE, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f,
                            Animation.RELATIVE_TO_SELF, 0.0f));
                } else {
                    animationSet.addAnimation(new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                            1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.ABSOLUTE, 0.0f,
                            Animation.ABSOLUTE, 0.0f));
                }
                animationSet.setDuration(duration);
            } else {
                mInAnimation.addAnimation(mHandleInAnimation);
            }
        }

        if (mHandleOutAnimation == null) {
            mHandleOutAnimation = new AlphaAnimation(1.0f, 0.0f);
            mHandleOutAnimation.setFillAfter(true);
            mHandleOutAnimation.setDuration(duration);
        }

        if (mOutAnimation == null) {
            mOutAnimation = new FastAnimationSet();
            if (!LauncherApplication.isScreenXLarge()) {
                final AnimationSet animationSet = mOutAnimation;
                animationSet.setInterpolator(new AccelerateInterpolator());
                animationSet.addAnimation(new AlphaAnimation(1.0f, 0.0f));
                if (mOrientation == ORIENTATION_HORIZONTAL) {
                    animationSet.addAnimation(new FastTranslateAnimation(Animation.ABSOLUTE, 0.0f,
                            Animation.ABSOLUTE, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                            Animation.RELATIVE_TO_SELF, 1.0f));
                } else {
                    animationSet.addAnimation(new FastTranslateAnimation(Animation.RELATIVE_TO_SELF,
                            0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.ABSOLUTE, 0.0f,
                            Animation.ABSOLUTE, 0.0f));
                }
                animationSet.setDuration(duration);
            } else {
                mOutAnimation.addAnimation(mHandleOutAnimation);
            }
        }
    }

    void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    private int getTransitionAnimationDuration() {
        return LauncherApplication.isScreenXLarge() ?
                XLARGE_TRANSITION_DURATION : TRANSITION_DURATION;
    }

    private int getAnimationDuration() {
        return LauncherApplication.isScreenXLarge() ?
                XLARGE_ANIMATION_DURATION : ANIMATION_DURATION;
    }

    private static class FastTranslateAnimation extends TranslateAnimation {
        public FastTranslateAnimation(int fromXType, float fromXValue, int toXType, float toXValue,
                int fromYType, float fromYValue, int toYType, float toYValue) {
            super(fromXType, fromXValue, toXType, toXValue,
                    fromYType, fromYValue, toYType, toYValue);
        }

        @Override
        public boolean willChangeTransformationMatrix() {
            return true;
        }

        @Override
        public boolean willChangeBounds() {
            return false;
        }
    }

    private static class FastAnimationSet extends AnimationSet {
        FastAnimationSet() {
            super(false);
        }

        @Override
        public boolean willChangeTransformationMatrix() {
            return true;
        }

        @Override
        public boolean willChangeBounds() {
            return false;
        }
    }
}
