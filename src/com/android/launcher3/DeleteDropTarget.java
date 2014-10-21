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

package com.android.launcher3;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.android.launcher3.compat.UserHandleCompat;

public class DeleteDropTarget extends ButtonDropTarget {
    private static int DELETE_ANIMATION_DURATION = 285;
    private static int FLING_DELETE_ANIMATION_DURATION = 350;
    private static float FLING_TO_DELETE_FRICTION = 0.035f;
    private static int MODE_FLING_DELETE_TO_TRASH = 0;
    private static int MODE_FLING_DELETE_ALONG_VECTOR = 1;

    private final int mFlingDeleteMode = MODE_FLING_DELETE_ALONG_VECTOR;

    private ColorStateList mOriginalTextColor;
    private TransitionDrawable mUninstallDrawable;
    private TransitionDrawable mRemoveDrawable;
    private TransitionDrawable mCurrentDrawable;

    private boolean mWaitingForUninstall = false;

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the drawable
        mOriginalTextColor = getTextColors();

        // Get the hover color
        Resources r = getResources();
        mHoverColor = r.getColor(R.color.delete_target_hover_tint);
        mUninstallDrawable = (TransitionDrawable) 
                r.getDrawable(R.drawable.uninstall_target_selector);
        mRemoveDrawable = (TransitionDrawable) r.getDrawable(R.drawable.remove_target_selector);

        mRemoveDrawable.setCrossFadeEnabled(true);
        mUninstallDrawable.setCrossFadeEnabled(true);

        // The current drawable is set to either the remove drawable or the uninstall drawable 
        // and is initially set to the remove drawable, as set in the layout xml.
        mCurrentDrawable = (TransitionDrawable) getCurrentDrawable();

        // Remove the text in the Phone UI in landscape
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!LauncherAppState.getInstance().isScreenLarge()) {
                setText("");
            }
        }
    }

    private boolean isAllAppsApplication(DragSource source, Object info) {
        return source.supportsAppInfoDropTarget() && (info instanceof AppInfo);
    }
    private boolean isAllAppsWidget(DragSource source, Object info) {
        if (source instanceof AppsCustomizePagedView) {
            if (info instanceof PendingAddItemInfo) {
                PendingAddItemInfo addInfo = (PendingAddItemInfo) info;
                switch (addInfo.itemType) {
                    case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                        return true;
                }
            }
        }
        return false;
    }
    private boolean isDragSourceWorkspaceOrFolder(DragObject d) {
        return (d.dragSource instanceof Workspace) || (d.dragSource instanceof Folder);
    }
    private boolean isWorkspaceOrFolderApplication(DragObject d) {
        return isDragSourceWorkspaceOrFolder(d) && (d.dragInfo instanceof ShortcutInfo);
    }
    private boolean isWorkspaceOrFolderWidget(DragObject d) {
        return isDragSourceWorkspaceOrFolder(d) && (d.dragInfo instanceof LauncherAppWidgetInfo);
    }
    private boolean isWorkspaceFolder(DragObject d) {
        return (d.dragSource instanceof Workspace) && (d.dragInfo instanceof FolderInfo);
    }

    private void setHoverColor() {
        if (mCurrentDrawable != null) {
            mCurrentDrawable.startTransition(mTransitionDuration);
        }
        setTextColor(mHoverColor);
    }
    private void resetHoverColor() {
        if (mCurrentDrawable != null) {
            mCurrentDrawable.resetTransition();
        }
        setTextColor(mOriginalTextColor);
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        return willAcceptDrop(d.dragInfo);
    }

    public static boolean willAcceptDrop(Object info) {
        if (info instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) info;
            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ||
                    item.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                return true;
            }

            if (!LauncherAppState.isDisableAllApps() &&
                    item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                return true;
            }

            if (!LauncherAppState.isDisableAllApps() &&
                    item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                    item instanceof AppInfo) {
                AppInfo appInfo = (AppInfo) info;
                return (appInfo.flags & AppInfo.DOWNLOADED_FLAG) != 0;
            }

            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                item instanceof ShortcutInfo) {
                if (LauncherAppState.isDisableAllApps()) {
                    ShortcutInfo shortcutInfo = (ShortcutInfo) info;
                    return (shortcutInfo.flags & AppInfo.DOWNLOADED_FLAG) != 0;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        boolean isVisible = true;
        boolean useUninstallLabel = !LauncherAppState.isDisableAllApps() &&
                isAllAppsApplication(source, info);
        boolean useDeleteLabel = !useUninstallLabel && source.supportsDeleteDropTarget();

        // If we are dragging an application from AppsCustomize, only show the control if we can
        // delete the app (it was downloaded), and rename the string to "uninstall" in such a case.
        // Hide the delete target if it is a widget from AppsCustomize.
        if (!willAcceptDrop(info) || isAllAppsWidget(source, info)) {
            isVisible = false;
        }
        if (useUninstallLabel) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                UserManager userManager = (UserManager)
                        getContext().getSystemService(Context.USER_SERVICE);
                Bundle restrictions = userManager.getUserRestrictions();
                if (restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
                        || restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false)) {
                    isVisible = false;
                }
            }
        }

        if (useUninstallLabel) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(mUninstallDrawable, null, null, null);
        } else if (useDeleteLabel) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(mRemoveDrawable, null, null, null);
        } else {
            isVisible = false;
        }
        mCurrentDrawable = (TransitionDrawable) getCurrentDrawable();

        mActive = isVisible;
        resetHoverColor();
        ((ViewGroup) getParent()).setVisibility(isVisible ? View.VISIBLE : View.GONE);
        if (isVisible && getText().length() > 0) {
            setText(useUninstallLabel ? R.string.delete_target_uninstall_label
                : R.string.delete_target_label);
        }
    }

    @Override
    public void onDragEnd() {
        super.onDragEnd();
        mActive = false;
    }

    public void onDragEnter(DragObject d) {
        super.onDragEnter(d);

        setHoverColor();
    }

    public void onDragExit(DragObject d) {
        super.onDragExit(d);

        if (!d.dragComplete) {
            resetHoverColor();
        } else {
            // Restore the hover color if we are deleting
            d.dragView.setColor(mHoverColor);
        }
    }

    private void animateToTrashAndCompleteDrop(final DragObject d) {
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);

        int width = mCurrentDrawable == null ? 0 : mCurrentDrawable.getIntrinsicWidth();
        int height = mCurrentDrawable == null ? 0 : mCurrentDrawable.getIntrinsicHeight();
        final Rect to = getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                width, height);
        final float scale = (float) to.width() / from.width();

        mSearchDropTargetBar.deferOnDragEnd();
        deferCompleteDropIfUninstalling(d);

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                completeDrop(d);
                mSearchDropTargetBar.onDragEnd();
                mLauncher.exitSpringLoadedDragModeDelayed(true, 0, null);
            }
        };
        dragLayer.animateView(d.dragView, from, to, scale, 1f, 1f, 0.1f, 0.1f,
                DELETE_ANIMATION_DURATION, new DecelerateInterpolator(2),
                new LinearInterpolator(), onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    private void deferCompleteDropIfUninstalling(DragObject d) {
        mWaitingForUninstall = false;
        if (isUninstallFromWorkspace(d)) {
            if (d.dragSource instanceof Folder) {
                ((Folder) d.dragSource).deferCompleteDropAfterUninstallActivity();
            } else if (d.dragSource instanceof Workspace) {
                ((Workspace) d.dragSource).deferCompleteDropAfterUninstallActivity();
            }
            mWaitingForUninstall = true;
        }
    }

    private boolean isUninstallFromWorkspace(DragObject d) {
        if (LauncherAppState.isDisableAllApps() && isWorkspaceOrFolderApplication(d)) {
            ShortcutInfo shortcut = (ShortcutInfo) d.dragInfo;
            // Only allow manifest shortcuts to initiate an un-install.
            return !InstallShortcutReceiver.isValidShortcutLaunchIntent(shortcut.intent);
        }
        return false;
    }

    private void completeDrop(DragObject d) {
        ItemInfo item = (ItemInfo) d.dragInfo;
        boolean wasWaitingForUninstall = mWaitingForUninstall;
        mWaitingForUninstall = false;
        if (isAllAppsApplication(d.dragSource, item)) {
            // Uninstall the application if it is being dragged from AppsCustomize
            AppInfo appInfo = (AppInfo) item;
            mLauncher.startApplicationUninstallActivity(appInfo.componentName, appInfo.flags,
                    appInfo.user);
        } else if (isUninstallFromWorkspace(d)) {
            ShortcutInfo shortcut = (ShortcutInfo) item;
            if (shortcut.intent != null && shortcut.intent.getComponent() != null) {
                final ComponentName componentName = shortcut.intent.getComponent();
                final DragSource dragSource = d.dragSource;
                final UserHandleCompat user = shortcut.user;
                mWaitingForUninstall = mLauncher.startApplicationUninstallActivity(
                        componentName, shortcut.flags, user);
                if (mWaitingForUninstall) {
                    final Runnable checkIfUninstallWasSuccess = new Runnable() {
                        @Override
                        public void run() {
                            mWaitingForUninstall = false;
                            String packageName = componentName.getPackageName();
                            boolean uninstallSuccessful = !AllAppsList.packageHasActivities(
                                    getContext(), packageName, user);
                            if (dragSource instanceof Folder) {
                                ((Folder) dragSource).
                                    onUninstallActivityReturned(uninstallSuccessful);
                            } else if (dragSource instanceof Workspace) {
                                ((Workspace) dragSource).
                                    onUninstallActivityReturned(uninstallSuccessful);
                            }
                        }
                    };
                    mLauncher.addOnResumeCallback(checkIfUninstallWasSuccess);
                }
            }
        } else if (isWorkspaceOrFolderApplication(d)) {
            LauncherModel.deleteItemFromDatabase(mLauncher, item);
        } else if (isWorkspaceFolder(d)) {
            // Remove the folder from the workspace and delete the contents from launcher model
            FolderInfo folderInfo = (FolderInfo) item;
            mLauncher.removeFolder(folderInfo);
            LauncherModel.deleteFolderContentsFromDatabase(mLauncher, folderInfo);
        } else if (isWorkspaceOrFolderWidget(d)) {
            // Remove the widget from the workspace
            mLauncher.removeAppWidget((LauncherAppWidgetInfo) item);
            LauncherModel.deleteItemFromDatabase(mLauncher, item);

            final LauncherAppWidgetInfo launcherAppWidgetInfo = (LauncherAppWidgetInfo) item;
            final LauncherAppWidgetHost appWidgetHost = mLauncher.getAppWidgetHost();
            if ((appWidgetHost != null) && launcherAppWidgetInfo.isWidgetIdValid()) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new AsyncTask<Void, Void, Void>() {
                    public Void doInBackground(Void ... args) {
                        appWidgetHost.deleteAppWidgetId(launcherAppWidgetInfo.appWidgetId);
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
            }
        }
        if (wasWaitingForUninstall && !mWaitingForUninstall) {
            if (d.dragSource instanceof Folder) {
                ((Folder) d.dragSource).onUninstallActivityReturned(false);
            } else if (d.dragSource instanceof Workspace) {
                ((Workspace) d.dragSource).onUninstallActivityReturned(false);
            }
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

        int width = mCurrentDrawable == null ? 0 : mCurrentDrawable.getIntrinsicWidth();
        int height = mCurrentDrawable == null ? 0 : mCurrentDrawable.getIntrinsicHeight();
        final Rect to = getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                width, height);
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
                float t = ((Float) animation.getAnimatedValue()).floatValue();
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
            float t = ((Float) animation.getAnimatedValue()).floatValue();
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
    };
    private AnimatorUpdateListener createFlingAlongVectorAnimatorListener(final DragLayer dragLayer,
            DragObject d, PointF vel, final long startTime, final int duration,
            ViewConfiguration config) {
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
            updateCb = createFlingAlongVectorAnimatorListener(dragLayer, d, vel, startTime,
                    duration, config);
        }
        deferCompleteDropIfUninstalling(d);

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
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
