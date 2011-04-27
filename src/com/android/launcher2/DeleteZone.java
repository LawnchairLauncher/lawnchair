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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import com.android.launcher.R;

public class DeleteZone extends IconDropTarget {
    private static final int ORIENTATION_HORIZONTAL = 1;
    private static final int TRANSITION_DURATION = 250;
    private static final int ANIMATION_DURATION = 200;
    private static final int XLARGE_TRANSITION_DURATION = 150;
    private static final int XLARGE_ANIMATION_DURATION = 200;
    private static final int LEFT_DRAWABLE = 0;

    private AnimatorSet mInAnimation;
    private AnimatorSet mOutAnimation;

    private int mOrientation;
    private DragController mDragController;

    private final RectF mRegionF = new RectF();
    private final Rect mRegion = new Rect();
    private TransitionDrawable mTransition;
    private int mTextColor;
    private int mDragTextColor;

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

        if (LauncherApplication.isScreenXLarge()) {
            int tb = getResources().getDimensionPixelSize(
                    R.dimen.delete_zone_vertical_drag_padding);
            int lr = getResources().getDimensionPixelSize(
                    R.dimen.delete_zone_horizontal_drag_padding);
            setDragPadding(tb, lr, tb, lr);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTransition = (TransitionDrawable) getCompoundDrawables()[LEFT_DRAWABLE];
        if (LauncherApplication.isScreenXLarge()) {
            mTransition.setCrossFadeEnabled(false);
        }

        Resources r = getResources();
        mTextColor = r.getColor(R.color.workspace_all_apps_and_delete_zone_text_color);
        mDragTextColor = r.getColor(R.color.workspace_delete_zone_drag_text_color);
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
        } else if (source instanceof Folder) {
            final Folder folder = (Folder) source;
            final FolderInfo folderInfo = (FolderInfo) folder.getInfo();
            // Item must be a ShortcutInfo otherwise it couldn't have been in the folder
            // in the first place.
            folderInfo.remove((ShortcutInfo)item);
        }

        if (item instanceof FolderInfo) {
            final FolderInfo folderInfo = (FolderInfo)item;
            LauncherModel.deleteFolderContentsFromDatabase(mLauncher, folderInfo);
            mLauncher.removeFolder(folderInfo);
        } else if (item instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo launcherAppWidgetInfo = (LauncherAppWidgetInfo) item;
            final LauncherAppWidgetHost appWidgetHost = mLauncher.getAppWidgetHost();
            if (appWidgetHost != null) {
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
            setTextColor(mDragTextColor);
            super.onDragEnter(source, x, y, xOffset, yOffset, dragView, dragInfo);
        }
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (mDragAndDropEnabled) {
            mTransition.reverseTransition(getTransitionAnimationDuration());
            setTextColor(mTextColor);
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
            mInAnimation.start();
            if (mOverlappingViews != null) {
                for (View view : mOverlappingViews) {
                    createOutAlphaAnim(view).start();
                }
            }
            setVisibility(VISIBLE);
        }
    }

    public void onDragEnd() {
        if (mActive && mDragAndDropEnabled) {
            mActive = false;
            mDragController.setDeleteRegion(null);

            mOutAnimation.start();
            if (mOverlappingViews != null) {
                for (View view : mOverlappingViews) {
                    createInAlphaAnim(view).start();
                }
            }
        }
    }

    private Animator createAlphaAnim(View v, float start, float end) {
        Animator anim = ObjectAnimator.ofFloat(v, "alpha", start, end);
        anim.setDuration(getAnimationDuration());
        return anim;
    }
    private Animator createInAlphaAnim(View v) {
        return createAlphaAnim(v, 0f, 1f);
    }
    private Animator createOutAlphaAnim(View v) {
        return createAlphaAnim(v, 1f, 0f);
    }

    private void createAnimations() {
        int duration = getAnimationDuration();

        Animator inAlphaAnim = createInAlphaAnim(this);
        if (mInAnimation == null) {
            mInAnimation = new AnimatorSet();
            mInAnimation.setInterpolator(new AccelerateInterpolator());
            mInAnimation.setDuration(duration);
            if (!LauncherApplication.isScreenXLarge()) {
                Animator translateAnim;
                if (mOrientation == ORIENTATION_HORIZONTAL) {
                    translateAnim = ObjectAnimator.ofFloat(this, "translationY", 
                            getMeasuredWidth(), 0f);
                } else {
                    translateAnim = ObjectAnimator.ofFloat(this, "translationX", 
                            getMeasuredHeight(), 0f);
                }
                mInAnimation.playTogether(translateAnim, inAlphaAnim);
            } else {
                mInAnimation.play(inAlphaAnim);
            }
        }

        Animator outAlphaAnim = createOutAlphaAnim(this);
        if (mOutAnimation == null) {
            mOutAnimation = new AnimatorSet();
            mOutAnimation.setInterpolator(new AccelerateInterpolator());
            mOutAnimation.setDuration(duration);
            if (!LauncherApplication.isScreenXLarge()) {
                Animator translateAnim;
                if (mOrientation == ORIENTATION_HORIZONTAL) {
                    translateAnim = ObjectAnimator.ofFloat(this, "translationY", 0f, 
                            getMeasuredWidth());
                } else {
                    translateAnim = ObjectAnimator.ofFloat(this, "translationX", 0f, 
                            getMeasuredHeight());
                }
                mOutAnimation.playTogether(translateAnim, outAlphaAnim);
            } else {
                mOutAnimation.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        setVisibility(GONE);
                    }
                });
                mOutAnimation.play(outAlphaAnim);
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
}
