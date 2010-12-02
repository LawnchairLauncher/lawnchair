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
import android.graphics.Paint;
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
import android.widget.ImageView;

import com.android.launcher.R;

public class DeleteZone extends ImageView implements DropTarget, DragController.DragListener {
    private static final int ORIENTATION_HORIZONTAL = 1;
    private static final int TRANSITION_DURATION = 250;
    private static final int ANIMATION_DURATION = 200;

    private final int[] mLocation = new int[2];
    
    private Launcher mLauncher;
    private boolean mTrashMode;

    /**
     * If true, this View responsible for managing its own visibility, and that of its handle.
     * This is generally the case, but it will be set to false when this is part of the
     * Contextual Action Bar.
     */
    private boolean mDragAndDropEnabled = true;

    private AnimationSet mInAnimation;
    private AnimationSet mOutAnimation;
    private Animation mHandleInAnimation;
    private Animation mHandleOutAnimation;

    private int mOrientation;
    private DragController mDragController;

    private final RectF mRegionF = new RectF();
    private final Rect mRegion = new Rect();
    private TransitionDrawable mTransition;
    private final Paint mTrashPaint = new Paint();

    /** The View that this view will replace. */
    private View mHandle = null;

    public DeleteZone(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteZone(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final int srcColor = context.getResources().getColor(R.color.delete_color_filter);
        mTrashPaint.setColorFilter(new PorterDuffColorFilter(srcColor, PorterDuff.Mode.SRC_ATOP));

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DeleteZone, defStyle, 0);
        mOrientation = a.getInt(R.styleable.DeleteZone_direction, ORIENTATION_HORIZONTAL);
        a.recycle();
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
        if (!mDragAndDropEnabled) return;
        mTransition.reverseTransition(TRANSITION_DURATION);
        dragView.setPaint(mTrashPaint);
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (!mDragAndDropEnabled) return;
        mTransition.reverseTransition(TRANSITION_DURATION);
        dragView.setPaint(null);
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        final ItemInfo item = (ItemInfo) info;
        if (item != null && mDragAndDropEnabled) {
            mTrashMode = true;
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
            if (mHandle != null) {
                mHandle.startAnimation(mHandleOutAnimation);
            }
            setVisibility(VISIBLE);
        }
    }

    public void onDragEnd() {
        if (mTrashMode && mDragAndDropEnabled) {
            mTrashMode = false;
            mDragController.setDeleteRegion(null);

            if (mOutAnimation != null) startAnimation(mOutAnimation);
            if (mHandleInAnimation != null && mHandle != null) {
                mHandle.startAnimation(mHandleInAnimation);
            }
            setVisibility(GONE);
        }
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    public void getHitRect(Rect outRect) {
        super.getHitRect(outRect);
        if (LauncherApplication.isScreenXLarge()) {
            // TODO: This is a temporary hack. mManageVisiblity = false when you're in CAB mode.
            // In that case, this icon is more tightly spaced next to the delete icon so we want
            // it to have a smaller drag region. When the new drag&drop system comes in, we'll
            // dispatch the drag/drop by calculating what target you're overlapping
            final int padding = R.dimen.delete_zone_padding;
            final int outerDragPadding =
                    getResources().getDimensionPixelSize(R.dimen.delete_zone_size);
            final int innerDragPadding = getResources().getDimensionPixelSize(padding);
            outRect.top -= outerDragPadding;
            outRect.left -= innerDragPadding;
            outRect.bottom += outerDragPadding;
            outRect.right += innerDragPadding;
        }
    }

    private void createAnimations() {
        if (mHandleInAnimation == null) {
            mHandleInAnimation = new AlphaAnimation(0.0f, 1.0f);
            mHandleInAnimation.setDuration(ANIMATION_DURATION);
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
                animationSet.setDuration(ANIMATION_DURATION);
            } else {
                mInAnimation.addAnimation(mHandleInAnimation);
            }
        }

        if (mHandleOutAnimation == null) {
            mHandleOutAnimation = new AlphaAnimation(1.0f, 0.0f);
            mHandleOutAnimation.setFillAfter(true);
            mHandleOutAnimation.setDuration(ANIMATION_DURATION);
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
                animationSet.setDuration(ANIMATION_DURATION);
            } else {
                mOutAnimation.addAnimation(mHandleOutAnimation);
            }
        }
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    void setHandle(View view) {
        mHandle = view;
    }

    void setDragAndDropEnabled(boolean enabled) {
        mDragAndDropEnabled = enabled;
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

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return null;
    }
}
