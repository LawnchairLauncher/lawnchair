/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.draganddrop;

import static android.app.StatusBarManager.DISABLE_NONE;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.content.pm.ActivityInfo.CONFIG_ASSETS_PATHS;
import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;

import static com.android.wm.shell.Flags.enableFlexibleSplit;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_BOTTOM;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_LEFT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_RIGHT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_TOP;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.DragEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsets.Type;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.logging.InstanceId;
import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.bar.DragToBubbleController;
import com.android.wm.shell.common.split.SplitScreenUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.animation.Interpolators;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.draganddrop.DragAndDropConstants;
import com.android.wm.shell.splitscreen.SplitScreenController;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Coordinates the visible drop targets for the current drag within a single display.
 */
public class DragLayout extends LinearLayout
        implements ViewTreeObserver.OnComputeInternalInsetsListener, DragLayoutProvider,
        DragZoneAnimator{

    static final boolean DEBUG_LAYOUT = false;
    // While dragging the status bar is hidden.
    private static final int HIDE_STATUS_BAR_FLAGS = StatusBarManager.DISABLE_NOTIFICATION_ICONS
            | StatusBarManager.DISABLE_NOTIFICATION_ALERTS
            | StatusBarManager.DISABLE_CLOCK
            | StatusBarManager.DISABLE_SYSTEM_INFO;

    private final DropTarget mPolicy;
    private final SplitScreenController mSplitScreenController;
    private final IconProvider mIconProvider;
    private final StatusBarManager mStatusBarManager;
    private final Configuration mLastConfiguration = new Configuration();

    // Whether this device supports left/right split in portrait
    private final boolean mAllowLeftRightSplitInPortrait;
    // Whether the device is currently in left/right split mode
    private boolean mIsLeftRightSplit;

    private SplitDragPolicy.Target mCurrentTarget = null;
    private final @Nullable DragToBubbleController mDragToBubbleController;
    private boolean mIsOverBubblesDropZone = false;
    private DropZoneView mDropZoneView1;
    private DropZoneView mDropZoneView2;
    private int mDisplayMargin;
    private int mDividerSize;
    private int mLaunchIntentEdgeMargin;
    private Insets mInsets = Insets.NONE;
    private Region mTouchableRegion;

    private boolean mIsShowing;
    private boolean mHasDropped;
    private DragSession mSession;
    // The last position that was handled by the drag layout
    private final Point mLastPosition = new Point();

    // Used with enableFlexibleSplit() flag
    private List<SplitDragPolicy.Target> mTargets;
    private Map<SplitDragPolicy.Target, DropZoneView> mTargetDropMap = new HashMap<>();
    private FrameLayout mAnimatingRootLayout;
    // Used with enableFlexibleSplit() flag

    @SuppressLint("WrongConstant")
    public DragLayout(Context context,
            SplitScreenController splitScreenController,
            @Nullable DragToBubbleController dragToBubbleController,
            IconProvider iconProvider) {
        super(context);
        mSplitScreenController = splitScreenController;
        mIconProvider = iconProvider;
        mDragToBubbleController = dragToBubbleController;
        mPolicy = new SplitDragPolicy(context, splitScreenController, this);
        mStatusBarManager = context.getSystemService(StatusBarManager.class);
        mLastConfiguration.setTo(context.getResources().getConfiguration());

        final Resources res = context.getResources();
        mDisplayMargin = res.getDimensionPixelSize(R.dimen.drop_layout_display_margin);
        mDividerSize = res.getDimensionPixelSize(R.dimen.split_divider_bar_width);
        mLaunchIntentEdgeMargin =
                res.getDimensionPixelSize(R.dimen.drag_launchable_intent_edge_margin);

        // Always use LTR because we assume dropZoneView1 is on the left and 2 is on the right when
        // showing the highlight.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        mDropZoneView1 = new DropZoneView(context);
        mDropZoneView2 = new DropZoneView(context);
        addView(mDropZoneView1, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        addView(mDropZoneView2, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        ((LayoutParams) mDropZoneView1.getLayoutParams()).weight = 1;
        ((LayoutParams) mDropZoneView2.getLayoutParams()).weight = 1;
        // We don't use the configuration orientation here to determine landscape because
        // near-square devices may report the same orietation with insets taken into account
        mAllowLeftRightSplitInPortrait = SplitScreenUtils.allowLeftRightSplitInPortrait(
                context.getResources());
        mIsLeftRightSplit = SplitScreenUtils.isLeftRightSplit(mAllowLeftRightSplitInPortrait,
                getResources().getConfiguration());
        setOrientation(mIsLeftRightSplit ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        updateContainerMargins(mIsLeftRightSplit);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTouchableRegion = Region.obtain();
        getViewTreeObserver().addOnComputeInternalInsetsListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnComputeInternalInsetsListener(this);
        mTouchableRegion.recycle();
    }

    @Override
    public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inOutInfo) {
        if (mSession != null && mSession.launchableIntent != null) {
            inOutInfo.touchableRegion.set(mTouchableRegion);
            inOutInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTouchableRegion();
    }

    /**
     * Updates the touchable region, this should be called after any configuration changes have
     * been applied.
     */
    private void updateTouchableRegion() {
        mTouchableRegion.setEmpty();
        if (mSession != null && mSession.launchableIntent != null) {
            final int width = getMeasuredWidth();
            final int height = getMeasuredHeight();
            if (mIsLeftRightSplit) {
                mTouchableRegion.union(
                        new Rect(0, 0, mInsets.left + mLaunchIntentEdgeMargin, height));
                mTouchableRegion.union(
                        new Rect(width - mInsets.right - mLaunchIntentEdgeMargin, 0, width,
                                height));
            } else {
                mTouchableRegion.union(
                        new Rect(0, 0, width, mInsets.top + mLaunchIntentEdgeMargin));
                mTouchableRegion.union(
                        new Rect(0, height - mInsets.bottom - mLaunchIntentEdgeMargin, width,
                                height));
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "Updating drag layout width=%d height=%d touchable region=%s",
                    width, height, mTouchableRegion);

            // Reapply insets to update the touchable region
            requestApplyInsets();
        }
    }


    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        mInsets = insets.getInsets(Type.tappableElement() | Type.displayCutout());
        recomputeDropTargets();

        boolean isLeftRightSplit = mSplitScreenController != null
                && mSplitScreenController.isLeftRightSplit();
        if (isLeftRightSplit) {
            if (enableFlexibleSplit()) {
                mTargetDropMap.values().forEach(dzv -> dzv.setBottomInset(mInsets.bottom));
            } else {
                mDropZoneView1.setBottomInset(mInsets.bottom);
                mDropZoneView2.setBottomInset(mInsets.bottom);
            }
        } else {
            if (enableFlexibleSplit()) {
                Collection<DropZoneView> dropViews = mTargetDropMap.values();
                final DropZoneView[] bottomView = {null};
                dropViews.forEach(dropZoneView -> {
                    bottomView[0] = dropZoneView;
                    dropZoneView.setBottomInset(0);
                });
                // TODO(b/349828130): necessary? maybe with UI polish
                //  bottomView[0].setBottomInset(mInsets.bottom);
            } else {
                mDropZoneView1.setBottomInset(0);
                mDropZoneView2.setBottomInset(mInsets.bottom);
            }
        }
        return super.onApplyWindowInsets(insets);
    }

    public void onConfigChanged(Configuration newConfig) {
        boolean isLeftRightSplit = SplitScreenUtils.isLeftRightSplit(mAllowLeftRightSplitInPortrait,
                newConfig);
        if (isLeftRightSplit != mIsLeftRightSplit) {
            mIsLeftRightSplit = isLeftRightSplit;
            setOrientation(mIsLeftRightSplit ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            updateContainerMargins(mIsLeftRightSplit);
        }

        final int diff = newConfig.diff(mLastConfiguration);
        final boolean themeChanged = (diff & CONFIG_ASSETS_PATHS) != 0
                || (diff & CONFIG_UI_MODE) != 0;
        if (themeChanged) {
            if (enableFlexibleSplit()) {
                mTargetDropMap.values().forEach(DropZoneView::onThemeChange);
            } else {
                mDropZoneView1.onThemeChange();
                mDropZoneView2.onThemeChange();
            }
        }
        mLastConfiguration.setTo(newConfig);
        requestLayout();
    }

    private void updateContainerMarginsForSingleTask() {
        if (enableFlexibleSplit()) {
            DropZoneView firstDropZone = mTargetDropMap.values().stream().findFirst().get();
            mTargetDropMap.values().stream()
                    .filter(dropZoneView -> dropZoneView != firstDropZone)
                    .forEach(dropZoneView -> dropZoneView.setContainerMargin(0, 0, 0, 0));
            firstDropZone.setContainerMargin(
                    mDisplayMargin, mDisplayMargin, mDisplayMargin, mDisplayMargin
            );
        } else {
            mDropZoneView1.setContainerMargin(
                    mDisplayMargin, mDisplayMargin, mDisplayMargin, mDisplayMargin);
            mDropZoneView2.setContainerMargin(0, 0, 0, 0);
        }
    }

    private void updateContainerMargins(boolean isLeftRightSplit) {
        final float halfMargin = mDisplayMargin / 2f;
        if (isLeftRightSplit) {
            mDropZoneView1.setContainerMargin(
                    mDisplayMargin, mDisplayMargin, halfMargin, mDisplayMargin);
            mDropZoneView2.setContainerMargin(
                    halfMargin, mDisplayMargin, mDisplayMargin, mDisplayMargin);
        } else {
            mDropZoneView1.setContainerMargin(
                    mDisplayMargin, mDisplayMargin, mDisplayMargin, halfMargin);
            mDropZoneView2.setContainerMargin(
                    mDisplayMargin, halfMargin, mDisplayMargin, mDisplayMargin);
        }
    }

    public boolean hasDropped() {
        return mHasDropped;
    }

    /**
     * Called when a new drag is started.
     */
    public void prepare(DragSession session, InstanceId loggerSessionId) {
        mPolicy.start(session, loggerSessionId);
        updateSession(session);
    }

    /**
     * Updates the drag layout based on the diven drag session.
     */
    public void updateSession(DragSession session) {
        // Note: The policy currently just keeps a reference to the session
        boolean updatingExistingSession = mSession != null;
        mSession = session;
        mHasDropped = false;
        mCurrentTarget = null;

        boolean alreadyInSplit = mSplitScreenController != null
                && mSplitScreenController.isSplitScreenVisible();
        if (!alreadyInSplit) {
            ActivityManager.RunningTaskInfo taskInfo1 = mSession.runningTaskInfo;
            if (taskInfo1 != null) {
                final int activityType = taskInfo1.getActivityType();
                if (activityType == ACTIVITY_TYPE_STANDARD) {
                    Drawable icon1 = mIconProvider.getIcon(taskInfo1.topActivityInfo);
                    int bgColor1 = getResizingBackgroundColor(taskInfo1);
                    mDropZoneView1.setAppInfo(bgColor1, icon1);
                    mDropZoneView2.setAppInfo(bgColor1, icon1);
                    mDropZoneView1.setForceIgnoreBottomMargin(false);
                    mDropZoneView2.setForceIgnoreBottomMargin(false);
                    updateDropZoneSizes(null, null); // passing null splits the views evenly
                } else {
                    // We use the first drop zone to show the fullscreen highlight, and don't need
                    // to set additional info
                    mDropZoneView1.setForceIgnoreBottomMargin(true);
                    updateDropZoneSizesForSingleTask();
                    updateContainerMarginsForSingleTask();
                }
            }
        } else {
            ActivityManager.RunningTaskInfo[] taskInfos = mSplitScreenController.getAllTaskInfos();
            boolean anyTasksNull = Arrays.stream(taskInfos).anyMatch(Objects::isNull);
            if (enableFlexibleSplit() && taskInfos != null && !anyTasksNull) {
                int i = 0;
                for (DropZoneView v : mTargetDropMap.values()) {
                    if (i >= taskInfos.length) {
                        // TODO(b/349828130) Support once we add 3 StageRoots
                        continue;
                    }
                    ActivityManager.RunningTaskInfo task = taskInfos[i];
                    v.setAppInfo(getResizingBackgroundColor(task),
                            mIconProvider.getIcon(task.topActivityInfo));
                    i++;
                }
            } else {
                // We're already in split so get taskInfo from the controller to populate icon / color.
                ActivityManager.RunningTaskInfo topOrLeftTask =
                        mSplitScreenController.getTaskInfo(SPLIT_POSITION_TOP_OR_LEFT);
                ActivityManager.RunningTaskInfo bottomOrRightTask =
                        mSplitScreenController.getTaskInfo(SPLIT_POSITION_BOTTOM_OR_RIGHT);
                if (topOrLeftTask != null && bottomOrRightTask != null) {
                    Drawable topOrLeftIcon = mIconProvider.getIcon(topOrLeftTask.topActivityInfo);
                    int topOrLeftColor = getResizingBackgroundColor(topOrLeftTask);
                    Drawable bottomOrRightIcon = mIconProvider.getIcon(
                            bottomOrRightTask.topActivityInfo);
                    int bottomOrRightColor = getResizingBackgroundColor(bottomOrRightTask);
                    mDropZoneView1.setAppInfo(topOrLeftColor, topOrLeftIcon);
                    mDropZoneView2.setAppInfo(bottomOrRightColor, bottomOrRightIcon);
                }
            }

            // Update the dropzones to match existing split sizes
            Rect topOrLeftBounds = new Rect();
            Rect bottomOrRightBounds = new Rect();
            mSplitScreenController.getStageBounds(topOrLeftBounds, bottomOrRightBounds);
            updateDropZoneSizes(topOrLeftBounds, bottomOrRightBounds);
        }
        requestLayout();
        if (updatingExistingSession) {
            // Update targets if we are already currently dragging
            recomputeDropTargets();
            update(mLastPosition.x, mLastPosition.y);
        }
    }

    private void updateDropZoneSizesForSingleTask() {
        resetDropZoneTranslations();

        final LinearLayout.LayoutParams dropZoneView1 =
                (LayoutParams) mDropZoneView1.getLayoutParams();
        final LinearLayout.LayoutParams dropZoneView2 =
                (LayoutParams) mDropZoneView2.getLayoutParams();
        dropZoneView1.width = MATCH_PARENT;
        dropZoneView1.height = MATCH_PARENT;
        dropZoneView2.width = 0;
        dropZoneView2.height = 0;
        dropZoneView1.weight = 1;
        dropZoneView2.weight = 0;
        mDropZoneView1.setLayoutParams(dropZoneView1);
        mDropZoneView2.setLayoutParams(dropZoneView2);
    }

    /** Zeroes out translationX and translationY on all drop zone views. */
    void resetDropZoneTranslations() {
        setDropZoneTranslations(0, 0);
    }

    /** Sets translationX and translationY on all drop zone views. */
    void setDropZoneTranslations(int x, int y) {
        mDropZoneView1.setTranslationX(x);
        mDropZoneView1.setTranslationY(y);
        mDropZoneView2.setTranslationX(x);
        mDropZoneView2.setTranslationY(y);
    }

    /**
     * Sets the size of the two drop zones based on the provided bounds. The divider sits between
     * the views and its size is included in the calculations.
     *
     * @param bounds1 bounds to apply to the first dropzone view, null if split in half.
     * @param bounds2 bounds to apply to the second dropzone view, null if split in half.
     */
    private void updateDropZoneSizes(Rect bounds1, Rect bounds2) {
        if (bounds1 == null || bounds2 == null) {
            // We're entering 50:50 split screen from a single app, no need for any translations.
            resetDropZoneTranslations();
        } else {
            // We're already in split, so align our drop zones to match the left/top app edge. This
            // is necessary because the left/top app can be offscreen.
            setDropZoneTranslations(bounds1.left, bounds1.top);
        }

        final int halfDivider = mDividerSize / 2;
        final LinearLayout.LayoutParams dropZoneView1 =
                (LayoutParams) mDropZoneView1.getLayoutParams();
        final LinearLayout.LayoutParams dropZoneView2 =
                (LayoutParams) mDropZoneView2.getLayoutParams();
        if (mIsLeftRightSplit) {
            dropZoneView1.width = bounds1 != null ? bounds1.width() + halfDivider : MATCH_PARENT;
            dropZoneView2.width = bounds2 != null ? bounds2.width() + halfDivider : MATCH_PARENT;
            dropZoneView1.height = MATCH_PARENT;
            dropZoneView2.height = MATCH_PARENT;
        } else {
            dropZoneView1.width = MATCH_PARENT;
            dropZoneView2.width = MATCH_PARENT;
            dropZoneView1.height = bounds1 != null ? bounds1.height() + halfDivider : MATCH_PARENT;
            dropZoneView2.height = bounds2 != null ? bounds2.height() + halfDivider : MATCH_PARENT;
        }
        dropZoneView1.weight = bounds1 != null ? 0 : 1;
        dropZoneView2.weight = bounds2 != null ? 0 : 1;
        mDropZoneView1.setLayoutParams(dropZoneView1);
        mDropZoneView2.setLayoutParams(dropZoneView2);
    }

    /**
     * Shows the drag layout.
     */
    public void show() {
        mIsShowing = true;
        recomputeDropTargets();
    }

    @NonNull
    @Override
    public void addDraggingView(ViewGroup rootView) {
        if (enableFlexibleSplit()) {
            removeAllViews();
            mAnimatingRootLayout = new FrameLayout(getContext());
            addView(mAnimatingRootLayout,
                    new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            ((LayoutParams) mAnimatingRootLayout.getLayoutParams()).weight = 1;
        }

        rootView.addView(this, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    /**
     * Recalculates the drop targets based on the current policy.
     */
    private void recomputeDropTargets() {
        if (!mIsShowing) {
            return;
        }
        final List<SplitDragPolicy.Target> targets = mPolicy.getTargets(mInsets);
        for (int i = 0; i < targets.size(); i++) {
            final SplitDragPolicy.Target target = targets.get(i);
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Add target: %s", target);
            // Inset the draw region by a little bit
            target.drawRegion.inset(mDisplayMargin, mDisplayMargin);
        }

        if (enableFlexibleSplit()) {
            mTargets = targets;
            mTargetDropMap.clear();
            for (int i = 0; i < mTargets.size(); i++) {
                DropZoneView v = new DropZoneView(getContext());
                SplitDragPolicy.Target t = mTargets.get(i);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(t.drawRegion.width(),
                        t.drawRegion.height());
                mAnimatingRootLayout.addView(v, params);
                v.setTranslationX(t.drawRegion.left);
                v.setTranslationY(t.drawRegion.top);
                mTargetDropMap.put(t, v);
                if (DEBUG_LAYOUT) {
                    v.setDebugIndex(t.index);
                }
            }
        }
    }

    /**
     * Updates the visible drop target as the user drags.
     */
    public void update(DragEvent event) {
        update((int) event.getX(), (int) event.getY());
    }

    /**
     * Updates the visible drop target as the user drags to the given coordinates.
     */
    private void update(int x, int y) {
        if (mHasDropped) {
            return;
        }
        // if event is over the bubble don't let split handle it
        if (interceptBubbleBarEvent(x, y)) {
            mLastPosition.set(x, y);
            return;
        }
        // Find containing region, if the same as mCurrentRegion, then skip, otherwise, animate the
        // visibility of the current region
        SplitDragPolicy.Target target = mPolicy.getTargetAtLocation(x, y);
        if (mCurrentTarget != target) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Current target: %s", target);
            if (target == null) {
                animateToNoTarget();
            } else if (mCurrentTarget == null) {
                if (mPolicy.getNumTargets() == 1) {
                    animateFullscreenContainer(true);
                } else {
                    animateSplitContainers(true, null /* animCompleteCallback */);
                    animateHighlight(target);
                }
            } else if (mCurrentTarget.type != target.type || enableFlexibleSplit()) {
                // Switching between targets
                if (enableFlexibleSplit()) {
                    animateHighlight(target);
                } else {
                    mDropZoneView1.animateSwitch();
                    mDropZoneView2.animateSwitch();
                }
                // Announce for accessibility.
                switch (target.type) {
                    case TYPE_SPLIT_LEFT:
                        mDropZoneView1.announceForAccessibility(
                                mContext.getString(R.string.accessibility_split_left));
                        break;
                    case TYPE_SPLIT_RIGHT:
                        mDropZoneView2.announceForAccessibility(
                                mContext.getString(R.string.accessibility_split_right));
                        break;
                    case TYPE_SPLIT_TOP:
                        mDropZoneView1.announceForAccessibility(
                                mContext.getString(R.string.accessibility_split_top));
                        break;
                    case TYPE_SPLIT_BOTTOM:
                        mDropZoneView2.announceForAccessibility(
                                mContext.getString(R.string.accessibility_split_bottom));
                        break;
                }
            }
            mCurrentTarget = target;
        }
        mLastPosition.set(x, y);
    }

    private boolean interceptBubbleBarEvent(int x, int y) {
        boolean interceptBubbleBarEvent = mDragToBubbleController != null
                && mDragToBubbleController.onDragUpdate(x, y);
        if (interceptBubbleBarEvent && !mIsOverBubblesDropZone && mCurrentTarget != null) {
            // only animate for no target if we first enter bubble bar drop zone and have a target
            animateToNoTarget();
            mCurrentTarget = null;
        }
        mIsOverBubblesDropZone = interceptBubbleBarEvent;
        return interceptBubbleBarEvent;
    }

    private void animateToNoTarget() {
        // Animating to no target
        animateSplitContainers(false, null /* animCompleteCallback */);
        if (enableFlexibleSplit()) {
            animateHighlight(null);
        }
    }

    /**
     * Hides the drag layout and animates out the visible drop targets.
     */
    public void hide(DragEvent event, Runnable hideCompleteCallback) {
        mIsShowing = false;
        mLastPosition.set(-1, -1);
        animateSplitContainers(false, () -> {
            if (hideCompleteCallback != null) {
                hideCompleteCallback.run();
            }
            switch (event.getAction()) {
                case DragEvent.ACTION_DROP:
                case DragEvent.ACTION_DRAG_ENDED:
                    mSession = null;
            }
        });
        if (mIsOverBubblesDropZone) {
            // bubble bar is still showing drop target, notify bubbles of drag cancel
            mIsOverBubblesDropZone = false;
            Objects.requireNonNull(mDragToBubbleController).onDragEnded();
        }
        // Reset the state if we previously force-ignore the bottom margin
        mDropZoneView1.setForceIgnoreBottomMargin(false);
        mDropZoneView2.setForceIgnoreBottomMargin(false);
        updateContainerMargins(mIsLeftRightSplit);
        mCurrentTarget = null;
        if (enableFlexibleSplit()) {
            mAnimatingRootLayout.removeAllViews();
        }
    }

    /**
     * Handles the drop onto a target and animates out the visible drop targets.
     */
    public boolean drop(DragEvent event, @NonNull SurfaceControl dragSurface,
            @Nullable WindowContainerToken hideTaskToken, Runnable dropCompleteCallback) {
        final boolean handledDrop = mCurrentTarget != null || mIsOverBubblesDropZone;
        mHasDropped = true;
        Intent appData = mSession.appData;

        // Process the drop exclusive by DropTarget OR by the BubbleBar
        if (mCurrentTarget != null) {
            mPolicy.onDropped(mCurrentTarget, hideTaskToken);
        } else if (appData != null
                && mIsOverBubblesDropZone
                && BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            handleDropOnBubbleBar(appData, Objects.requireNonNull(mDragToBubbleController));
        }
        mIsOverBubblesDropZone = false;

        // Start animating the drop UI out with the drag surface
        hide(event, dropCompleteCallback);
        if (handledDrop) {
            hideDragSurface(dragSurface);
        }
        return handledDrop;
    }

    private void handleDropOnBubbleBar(Intent appData,
            DragToBubbleController dragToBubbleController) {
        ShortcutInfo shortcutInfo = appData.getParcelableExtra(
                DragAndDropConstants.EXTRA_SHORTCUT_INFO,
                ShortcutInfo.class
        );
        if (shortcutInfo != null) {
            dragToBubbleController.onItemDropped(shortcutInfo);
            return;
        }
        UserHandle user = appData.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
        PendingIntent pendingIntent = appData.getParcelableExtra(
                ClipDescription.EXTRA_PENDING_INTENT,
                PendingIntent.class
        );
        if (pendingIntent != null && user != null) {
            dragToBubbleController.onItemDropped(pendingIntent, user);
        }
    }

    private void hideDragSurface(@NonNull SurfaceControl dragSurface) {
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        final ValueAnimator dragSurfaceAnimator = ValueAnimator.ofFloat(0f, 1f);
        // Currently the splash icon animation runs with the default ValueAnimator duration of
        // 300ms
        dragSurfaceAnimator.setDuration(300);
        dragSurfaceAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        dragSurfaceAnimator.addUpdateListener(animation -> {
            float t = animation.getAnimatedFraction();
            float alpha = 1f - t;
            // TODO: Scale the drag surface as well once we make all the source surfaces
            //       consistent
            tx.setAlpha(dragSurface, alpha);
            tx.apply();
        });
        dragSurfaceAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCanceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cleanUpSurface();
                mCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCanceled) {
                    // Already handled above
                    return;
                }
                cleanUpSurface();
            }

            private void cleanUpSurface() {
                // Clean up the drag surface
                tx.remove(dragSurface);
                tx.apply();
            }
        });
        dragSurfaceAnimator.start();
    }

    private void animateFullscreenContainer(boolean visible) {
        mStatusBarManager.disable(visible
                ? HIDE_STATUS_BAR_FLAGS
                : DISABLE_NONE);
        // We're only using the first drop zone if there is one fullscreen target
        mDropZoneView1.setShowingMargin(visible);
        mDropZoneView1.setShowingHighlight(visible);
    }

    private void animateSplitContainers(boolean visible, Runnable animCompleteCallback) {
        mStatusBarManager.disable(visible
                ? HIDE_STATUS_BAR_FLAGS
                : DISABLE_NONE);
        Animator animator;
        if (enableFlexibleSplit()) {
            DropZoneView anyDropZoneView = null;
            for (DropZoneView dz : mTargetDropMap.values()) {
                dz.setShowingMargin(visible);
                anyDropZoneView = dz;
            }
            animator = anyDropZoneView != null ? anyDropZoneView.getAnimator() : null;
        } else {
            mDropZoneView1.setShowingMargin(visible);
            mDropZoneView2.setShowingMargin(visible);
            animator = mDropZoneView1.getAnimator();
        }

        if (animCompleteCallback != null) {
            if (animator != null) {
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animCompleteCallback.run();
                    }
                });
            } else {
                // If there's no animator the animation is done so run immediately
                animCompleteCallback.run();
            }
        }
    }

    @Override
    public void animateDragTargets(
            @NonNull List<? extends BiConsumer<SplitDragPolicy.Target, View>> viewsToAnimate) {
        for (Map.Entry<SplitDragPolicy.Target, DropZoneView> entry : mTargetDropMap.entrySet()) {
            viewsToAnimate.get(0).accept(entry.getKey(), entry.getValue());
        }
    }

    private void animateHighlight(SplitDragPolicy.Target target) {
        if (enableFlexibleSplit()) {
            for (Map.Entry<SplitDragPolicy.Target, DropZoneView> dzv : mTargetDropMap.entrySet()) {
                // Highlight the view w/ the matching target, unhighlight the rest
                dzv.getValue().setShowingHighlight(dzv.getKey() == target);
            }
            mPolicy.onHoveringOver(target);
            return;
        }

        if (target.type == TYPE_SPLIT_LEFT || target.type == TYPE_SPLIT_TOP) {
            mDropZoneView1.setShowingHighlight(true);
            mDropZoneView2.setShowingHighlight(false);
        } else if (target.type == TYPE_SPLIT_RIGHT || target.type == TYPE_SPLIT_BOTTOM) {
            mDropZoneView1.setShowingHighlight(false);
            mDropZoneView2.setShowingHighlight(true);
        }
    }

    private static int getResizingBackgroundColor(ActivityManager.RunningTaskInfo taskInfo) {
        final int taskBgColor = taskInfo.taskDescription.getBackgroundColor();
        return Color.valueOf(taskBgColor == -1 ? Color.WHITE : taskBgColor).toArgb();
    }

    /**
     * Dumps information about this drag layout.
     */
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + "DragLayout:");
        pw.println(innerPrefix + "mIsLeftRightSplitInPortrait=" + mAllowLeftRightSplitInPortrait);
        pw.println(innerPrefix + "mIsLeftRightSplit=" + mIsLeftRightSplit);
        pw.println(innerPrefix + "mDisplayMargin=" + mDisplayMargin);
        pw.println(innerPrefix + "mDividerSize=" + mDividerSize);
        pw.println(innerPrefix + "mIsShowing=" + mIsShowing);
        pw.println(innerPrefix + "mHasDropped=" + mHasDropped);
        pw.println(innerPrefix + "mCurrentTarget=" + mCurrentTarget);
        pw.println(innerPrefix + "mInsets=" + mInsets);
        pw.println(innerPrefix + "mTouchableRegion=" + mTouchableRegion);
    }
}
