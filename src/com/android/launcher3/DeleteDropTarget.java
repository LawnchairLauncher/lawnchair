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
import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.android.launcher3.util.FlingAnimation;
import com.android.launcher3.util.Thunk;

public class DeleteDropTarget extends ButtonDropTarget {

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.delete_target_hover_tint);

        setDrawable(R.drawable.ic_remove_launcher);
    }

    public static boolean supportsDrop(Object info) {
        return (info instanceof ShortcutInfo)
                || (info instanceof LauncherAppWidgetInfo)
                || (info instanceof FolderInfo);
    }

    @Override
    protected boolean supportsDrop(DragSource source, Object info) {
        return source.supportsDeleteDropTarget() && supportsDrop(info);
    }

    @Override
    @Thunk void completeDrop(DragObject d) {
        ItemInfo item = (ItemInfo) d.dragInfo;
        if ((d.dragSource instanceof Workspace) || (d.dragSource instanceof Folder)) {
            removeWorkspaceOrFolderItem(mLauncher, item, null);
        }
    }

    /**
     * Removes the item from the workspace. If the view is not null, it also removes the view.
     */
    public static void removeWorkspaceOrFolderItem(Launcher launcher, ItemInfo item, View view) {
        // Remove the item from launcher and the db, we can ignore the containerInfo in this call
        // because we already remove the drag view from the folder (if the drag originated from
        // a folder) in Folder.beginDrag()
        launcher.removeItem(view, item, true /* deleteFromDb */);
        launcher.getWorkspace().stripEmptyScreens();
        launcher.getDragLayer().announceForAccessibility(launcher.getString(R.string.item_removed));
    }

    @Override
    public void onFlingToDelete(final DragObject d, PointF vel) {
        // Don't highlight the icon as it's animating
        d.dragView.setColor(0);
        d.dragView.updateInitialScaleToCurrentScale();

        final DragLayer dragLayer = mLauncher.getDragLayer();
        FlingAnimation fling = new FlingAnimation(d, vel,
                getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                        mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight()),
                        dragLayer);

        final int duration = fling.getDuration();
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

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragMode();
                completeDrop(d);
                mLauncher.getDragController().onDeferredEndFling(d);
            }
        };

        dragLayer.animateView(d.dragView, fling, duration, tInterpolator, onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }
}
