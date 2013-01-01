/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class DeleteDropTarget extends ButtonDropTarget {
    private static final int DELETE_ANIMATION_DURATION = 285;
    private static final int FLING_DELETE_ANIMATION_DURATION = 350;
    private static final float FLING_TO_DELETE_FRICTION = 0.035f;
    private static final int MODE_FLING_DELETE_TO_TRASH = 0;
    private static final int MODE_FLING_DELETE_ALONG_VECTOR = 1;

    private final int mFlingDeleteMode = MODE_FLING_DELETE_ALONG_VECTOR;

    private static final int MODE_DELETE = 0;
    private static final int MODE_UNINSTALL = 1;
    private int mMode = MODE_DELETE;

    private ColorStateList mOriginalTextColor;
    private Drawable mUninstallActiveDrawable;
    private Drawable mRemoveActiveDrawable;
    private Drawable mRemoveNormalDrawable;
    private Drawable mCurrentDrawable;
    private boolean mUninstall;

    private final Handler mHandler = new Handler();

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private final Runnable mShowUninstaller = new Runnable() {
        public void run() {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            switchToUninstallTarget();
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the drawable
        mOriginalTextColor = getTextColors();

        // Get the hover color
        Resources r = getResources();
        mHoverColor = r.getColor(R.color.delete_target_hover_tint);
        mUninstallActiveDrawable = r.getDrawable(R.drawable.ic_launcher_trashcan_active_holo);
        mRemoveActiveDrawable = r.getDrawable(R.drawable.ic_launcher_clear_active_holo);
        mRemoveNormalDrawable = r.getDrawable(R.drawable.ic_launcher_clear_normal_holo);

        // Remove the text in the Phone UI in landscape
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!LauncherApplication.isScreenLarge()) {
                setText("");
            }
        }
    }

    private boolean isAllAppsItem(DragSource source, Object info) {
        return isAllAppsApplication(source, info) || isAllAppsWidget(source, info);
    }
    private boolean isAllAppsApplication(DragSource source, Object info) {
        return (source instanceof AppsCustomizePagedView) && (info instanceof ApplicationInfo);
    }
    private boolean isAllAppsWidget(DragSource source, Object info) {
        if (source instanceof AppsCustomizePagedView) {
            if (info instanceof PendingAddItemInfo) {
                PendingAddItemInfo addInfo = (PendingAddItemInfo) info;
                switch (addInfo.itemType) {
                    case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                    case LauncherSettings.Favorites.ITEM_TYPE_ALLAPPS:
                        return true;
                }
            }
        }
        return false;
    }
    private boolean isDragSourceWorkspaceOrFolder(DragSource source) {
        return (source instanceof Workspace) || (source instanceof Folder);
    }
    private boolean isWorkspaceOrFolderApplication(DragSource source, Object info) {
        return isDragSourceWorkspaceOrFolder(source) && (info instanceof ShortcutInfo);
    }
    private boolean isWorkspaceWidget(DragSource source, Object info) {
        return isDragSourceWorkspaceOrFolder(source) && (info instanceof LauncherAppWidgetInfo);
    }
    private boolean isWorkspaceFolder(DragSource source, Object info) {
        return (source instanceof Workspace) && (info instanceof FolderInfo);
    }

    private void setHoverColor() {
        setTextColor(mHoverColor);
    }
    private void resetHoverColor() {
        setTextColor(mOriginalTextColor);
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        if (d.dragInfo instanceof ShortcutInfo) {
            if (((ShortcutInfo) d.dragInfo).itemType == LauncherSettings.Favorites.ITEM_TYPE_ALLAPPS) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        boolean isUninstall = false;
        boolean isVisible = true;

        // If we are dragging an application from AppsCustomize, only show the uninstall control if we
        // can delete the app (it was downloaded)
        if (isAllAppsApplication(source, info)) {
            ApplicationInfo appInfo = (ApplicationInfo) info;
            if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) != 0) {
                isUninstall = true;
            }
        } else if (isWorkspaceOrFolderApplication(source, info)) {
            ShortcutInfo shortcutInfo = (ShortcutInfo) info;
            PackageManager pm = getContext().getPackageManager();
            if (shortcutInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_ALLAPPS) {
                ResolveInfo resolveInfo = pm.resolveActivity(shortcutInfo.intent, 0);
                if (resolveInfo != null && (resolveInfo.activityInfo.applicationInfo.flags &
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    isUninstall = true;
                }
            } else {
                isVisible = false;
            }
        }

        setCompoundDrawablesWithIntrinsicBounds(mRemoveNormalDrawable, null, null, null);
        mCurrentDrawable = getCompoundDrawables()[0];

        mUninstall = isUninstall;
        mActive = isVisible;
        mMode = MODE_DELETE;

        setTextColor(mOriginalTextColor);
        resetHoverColor();
        ((ViewGroup) getParent()).setVisibility(isVisible ? View.VISIBLE : View.GONE);
        if (getText().length() > 0) {
            if (isAllAppsItem(source, info)) {
                setText(R.string.cancel_target_label);
            } else {
                setText(R.string.delete_target_label);
            }
        }
    }

    private void switchToUninstallTarget() {
        if (!mUninstall) {
            return;
        }

        mMode = MODE_UNINSTALL;

        if (getText().length() > 0) {
            setText(R.string.delete_target_uninstall_label);
        }

        setCompoundDrawablesWithIntrinsicBounds(mUninstallActiveDrawable, null, null, null);
        mCurrentDrawable = getCompoundDrawables()[0];
    }

    @Override
    public void onDragEnd() {
        super.onDragEnd();

        mActive = false;
    }

    public void onDragEnter(DragObject d) {
        super.onDragEnter(d);

        if (mUninstall) {
            mHandler.removeCallbacks(mShowUninstaller);
            mHandler.postDelayed(mShowUninstaller, 1000);
        }

        setCompoundDrawablesWithIntrinsicBounds(mRemoveActiveDrawable, null, null, null);
        mCurrentDrawable = getCompoundDrawables()[0];

        setHoverColor();
    }

    public void onDragExit(DragObject d) {
        super.onDragExit(d);

        mHandler.removeCallbacks(mShowUninstaller);

        if (!d.dragComplete) {
            mMode = MODE_DELETE;

            if (getText().length() > 0) {
                if (isAllAppsItem(d.dragSource, d.dragInfo)) {
                    setText(R.string.cancel_target_label);
                } else {
                    setText(R.string.delete_target_label);
                }
            }

            setCompoundDrawablesWithIntrinsicBounds(mRemoveNormalDrawable, null, null, null);
            mCurrentDrawable = getCompoundDrawables()[0];
            resetHoverColor();
        } else {
            // Restore the hover color if we are deleting
            d.dragView.setColor(mHoverColor);
        }
    }

    private void animateToTrashAndCompleteDrop(final DragObject d) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);
        Rect to = getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                mCurrentDrawable.getIntrinsicWidth(), mCurrentDrawable.getIntrinsicHeight());
        float scale = (float) to.width() / from.width();

        mSearchDropTargetBar.deferOnDragEnd();
        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mSearchDropTargetBar.onDragEnd();
                mLauncher.exitSpringLoadedDragMode();
                completeDrop(d);
            }
        };
        dragLayer.animateView(d.dragView, from, to, scale, 1f, 1f, 0.1f, 0.1f,
                DELETE_ANIMATION_DURATION, new DecelerateInterpolator(2),
                new LinearInterpolator(), onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    private void completeDrop(DragObject d) {
        ItemInfo item = (ItemInfo) d.dragInfo;

        switch (mMode) {
            case MODE_DELETE:
                if (isWorkspaceOrFolderApplication(d.dragSource, item)) {
                    LauncherModel.deleteItemFromDatabase(mLauncher, item);
                } else if (isWorkspaceFolder(d.dragSource, d.dragInfo)) {
                    // Remove the folder from the workspace and delete the contents from launcher model
                    FolderInfo folderInfo = (FolderInfo) item;
                    mLauncher.removeFolder(folderInfo);
                    LauncherModel.deleteFolderContentsFromDatabase(mLauncher, folderInfo);
                } else if (isWorkspaceWidget(d.dragSource, item)) {
                    // Remove the widget from the workspace
                    mLauncher.removeAppWidget((LauncherAppWidgetInfo) item);
                    LauncherModel.deleteItemFromDatabase(mLauncher, item);

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
                break;
            case MODE_UNINSTALL:
                if (isAllAppsApplication(d.dragSource, item)) {
                    // Uninstall the application
                    mLauncher.startApplicationUninstallActivity((ApplicationInfo) item);
                } else if (isWorkspaceOrFolderApplication(d.dragSource, item)) {
                    // Uninstall the shortcut
                    mLauncher.startShortcutUninstallActivity((ShortcutInfo) item);
                }
                break;
        }
    }

    public void onDrop(DragObject d) {
        animateToTrashAndCompleteDrop(d);
    }

    /**
     * Creates an animation from the current drag view to the delete trash icon.
     */
    private AnimatorUpdateListener createFlingToTrashAnimatorListener(final DragLayer dragLayer,
            DragObject d, PointF vel, ViewConfiguration config) {
        final Rect to = getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                mCurrentDrawable.getIntrinsicWidth(), mCurrentDrawable.getIntrinsicHeight());
        final Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);

        // Calculate how far along the velocity vector we should put the intermediate point on
        // the bezier curve
        float velocity = Math.abs(vel.length());
        float vp = Math.min(1f, velocity / (config.getScaledMaximumFlingVelocity() / 2f));
        int offsetY = (int) (-from.top * vp);
        int offsetX = (int) (offsetY / (vel.y / vel.x));
        final float y2 = from.top + offsetY;                        // intermediate t/l
        final float x2 = from.left + offsetX;
        final float x1 = from.left;                                 // drag view t/l
        final float y1 = from.top;
        final float x3 = to.left;                                   // delete target t/l
        final float y3 = to.top;

        final TimeInterpolator scaleAlphaInterpolator = new TimeInterpolator() {
            @Override
            public float getInterpolation(float t) {
                return t * t * t * t * t * t * t * t;
            }
        };
        return new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final DragView dragView = (DragView) dragLayer.getAnimatedView();
                float t = (Float) animation.getAnimatedValue();
                float tp = scaleAlphaInterpolator.getInterpolation(t);
                float initialScale = dragView.getInitialScale();
                float finalAlpha = 0.5f;
                float scale = dragView.getScaleX();
                float x1o = ((1f - scale) * dragView.getMeasuredWidth()) / 2f;
                float y1o = ((1f - scale) * dragView.getMeasuredHeight()) / 2f;
                float x = (1f - t) * (1f - t) * (x1 - x1o) + 2 * (1f - t) * t * (x2 - x1o) +
                        (t * t) * x3;
                float y = (1f - t) * (1f - t) * (y1 - y1o) + 2 * (1f - t) * t * (y2 - x1o) +
                        (t * t) * y3;

                dragView.setTranslationX(x);
                dragView.setTranslationY(y);
                dragView.setScaleX(initialScale * (1f - tp));
                dragView.setScaleY(initialScale * (1f - tp));
                dragView.setAlpha(finalAlpha + (1f - finalAlpha) * (1f - tp));
            }
        };
    }

    /**
     * Creates an animation from the current drag view along its current velocity vector.
     * For this animation, the alpha runs for a fixed duration and we update the position
     * progressively.
     */
    private static class FlingAlongVectorAnimatorUpdateListener implements AnimatorUpdateListener {
        private DragLayer mDragLayer;
        private PointF mVelocity;
        private Rect mFrom;
        private long mPrevTime;
        private boolean mHasOffsetForScale;
        private float mFriction;

        private final TimeInterpolator mAlphaInterpolator = new DecelerateInterpolator(0.75f);

        public FlingAlongVectorAnimatorUpdateListener(DragLayer dragLayer, PointF vel, Rect from,
                long startTime, float friction) {
            mDragLayer = dragLayer;
            mVelocity = vel;
            mFrom = from;
            mPrevTime = startTime;
            mFriction = 1f - (dragLayer.getResources().getDisplayMetrics().density * friction);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final DragView dragView = (DragView) mDragLayer.getAnimatedView();
            float t = (Float) animation.getAnimatedValue();
            long curTime = AnimationUtils.currentAnimationTimeMillis();

            if (!mHasOffsetForScale) {
                mHasOffsetForScale = true;
                float scale = dragView.getScaleX();
                float xOffset = ((scale - 1f) * dragView.getMeasuredWidth()) / 2f;
                float yOffset = ((scale - 1f) * dragView.getMeasuredHeight()) / 2f;

                mFrom.left += xOffset;
                mFrom.top += yOffset;
            }

            mFrom.left += (mVelocity.x * (curTime - mPrevTime) / 1000f);
            mFrom.top += (mVelocity.y * (curTime - mPrevTime) / 1000f);

            dragView.setTranslationX(mFrom.left);
            dragView.setTranslationY(mFrom.top);
            dragView.setAlpha(1f - mAlphaInterpolator.getInterpolation(t));

            mVelocity.x *= mFriction;
            mVelocity.y *= mFriction;
            mPrevTime = curTime;
        }
    }
    private AnimatorUpdateListener createFlingAlongVectorAnimatorListener(final DragLayer dragLayer,
            DragObject d, PointF vel, final long startTime) {
        final Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);

        return new FlingAlongVectorAnimatorUpdateListener(dragLayer, vel, from, startTime,
                FLING_TO_DELETE_FRICTION);
    }

    public void onFlingToDelete(final DragObject d, int x, int y, PointF vel) {
        final boolean isAllApps = d.dragSource instanceof AppsCustomizePagedView;

        // Don't highlight the icon as it's animating
        d.dragView.setColor(0);
        d.dragView.updateInitialScaleToCurrentScale();
        // Don't highlight the target if we are flinging from AllApps
        if (isAllApps) {
            resetHoverColor();
        }

        if (mFlingDeleteMode == MODE_FLING_DELETE_TO_TRASH) {
            // Defer animating out the drop target if we are animating to it
            mSearchDropTargetBar.deferOnDragEnd();
            mSearchDropTargetBar.finishAnimations();
        }

        final ViewConfiguration config = ViewConfiguration.get(mLauncher);
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final int duration = FLING_DELETE_ANIMATION_DURATION;
        final long startTime = AnimationUtils.currentAnimationTimeMillis();

        // NOTE: Because it takes time for the first frame of animation to actually be
        // called and we expect the animation to be a continuation of the fling, we have
        // to account for the time that has elapsed since the fling finished.  And since
        // we don't have a startDelay, we will always get call to update when we call
        // start() (which we want to ignore).
        final TimeInterpolator tInterpolator = new TimeInterpolator() {
            private int mCount = -1;
            private float mOffset = 0f;

            @Override
            public float getInterpolation(float t) {
                if (mCount < 0) {
                    mCount++;
                } else if (mCount == 0) {
                    mOffset = Math.min(0.5f, (float) (AnimationUtils.currentAnimationTimeMillis() -
                            startTime) / duration);
                    mCount++;
                }
                return Math.min(1f, mOffset + t);
            }
        };
        AnimatorUpdateListener updateCb = null;
        if (mFlingDeleteMode == MODE_FLING_DELETE_TO_TRASH) {
            updateCb = createFlingToTrashAnimatorListener(dragLayer, d, vel, config);
        } else if (mFlingDeleteMode == MODE_FLING_DELETE_ALONG_VECTOR) {
            updateCb = createFlingAlongVectorAnimatorListener(dragLayer, d, vel, startTime);
        }
        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mSearchDropTargetBar.onDragEnd();

                // If we are dragging from AllApps, then we allow AppsCustomizePagedView to clean up
                // itself, otherwise, complete the drop to initiate the deletion process
                if (!isAllApps) {
                    mLauncher.exitSpringLoadedDragMode();
                    completeDrop(d);
                }
                mLauncher.getDragController().onDeferredEndFling(d);
            }
        };
        dragLayer.animateView(d.dragView, updateCb, duration, tInterpolator, onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }
}
