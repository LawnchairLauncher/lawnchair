/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

import static java.util.stream.Collectors.toList;

import android.annotation.BinderThread;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;

import com.android.launcher3.taskbar.TaskbarSharedState;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.util.Executors.SimpleThreadFactory;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.wm.shell.Flags;
import com.android.wm.shell.bubbles.IBubblesListener;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.bubbles.BubbleBarUpdate;
import com.android.wm.shell.shared.bubbles.BubbleInfo;
import com.android.wm.shell.shared.bubbles.RemovedBubble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This registers a listener with SysUIProxy to get information about changes to the bubble
 * stack state from WMShell (SysUI). The controller is also responsible for loading the necessary
 * information to render each of the bubbles & dispatches changes to
 * {@link BubbleBarViewController} which will then update {@link BubbleBarView} as needed.
 *
 * <p>For details around the behavior of the bubble bar, see {@link BubbleBarView}.
 */
public class BubbleBarController extends IBubblesListener.Stub {

    private static final String TAG = "BubbleBarController";
    private static final boolean DEBUG = false;

    /**
     * Determines whether bubbles can be shown in the bubble bar. This value updates when the
     * taskbar is recreated.
     *
     * @see #onTaskbarRecreated()
     */
    private static boolean sBubbleBarEnabled = Flags.enableBubbleBar()
            || SystemProperties.getBoolean("persist.wm.debug.bubble_bar", false);

    /** Whether showing bubbles in the launcher bubble bar is enabled. */
    public static boolean isBubbleBarEnabled() {
        return sBubbleBarEnabled;
    }

    /** Re-reads the value of the flag from SystemProperties when taskbar is recreated. */
    public static void onTaskbarRecreated() {
        sBubbleBarEnabled = Flags.enableBubbleBar()
                || SystemProperties.getBoolean("persist.wm.debug.bubble_bar", false);
    }

    private static final long MASK_HIDE_BUBBLE_BAR = SYSUI_STATE_BOUNCER_SHOWING
            | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
            | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED
            | SYSUI_STATE_IME_VISIBLE
            | SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
            | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;

    private static final long MASK_HIDE_HANDLE_VIEW = SYSUI_STATE_BOUNCER_SHOWING
            | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
            | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED
            | SYSUI_STATE_IME_VISIBLE;

    private static final long MASK_SYSUI_LOCKED = SYSUI_STATE_BOUNCER_SHOWING
            | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
            | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

    private final Context mContext;
    private final BubbleBarView mBarView;
    private final ArrayMap<String, BubbleBarBubble> mBubbles = new ArrayMap<>();
    private final ArrayMap<String, BubbleBarBubble> mSuppressedBubbles = new ArrayMap<>();

    private static final Executor BUBBLE_STATE_EXECUTOR = Executors.newSingleThreadExecutor(
            new SimpleThreadFactory("BubbleStateUpdates-", THREAD_PRIORITY_BACKGROUND));
    private final SystemUiProxy mSystemUiProxy;

    private BubbleBarItem mSelectedBubble;

    private TaskbarSharedState mSharedState;
    private BubbleBarViewController mBubbleBarViewController;
    private BubbleStashController mBubbleStashController;
    private Optional<BubbleStashedHandleViewController> mBubbleStashedHandleViewController;
    private BubblePinController mBubblePinController;
    private BubbleCreator mBubbleCreator;
    private BubbleBarLocationListener mBubbleBarLocationListener;

    private int mLastSentBubbleBarTopToScreenBottom;

    private boolean mIsImeVisible = false;

    /**
     * Similar to {@link BubbleBarUpdate} but rather than {@link BubbleInfo}s it uses
     * {@link BubbleBarBubble}s so that it can be used to update the views.
     */
    private static class BubbleBarViewUpdate {
        final boolean initialState;
        boolean expandedChanged;
        boolean expanded;
        boolean shouldShowEducation;
        String selectedBubbleKey;
        String suppressedBubbleKey;
        String unsuppressedBubbleKey;
        BubbleBarLocation bubbleBarLocation;
        List<RemovedBubble> removedBubbles;
        List<String> bubbleKeysInOrder;
        Point expandedViewDropTargetSize;
        boolean showOverflow;
        boolean showOverflowChanged;

        // These need to be loaded in the background
        BubbleBarBubble addedBubble;
        BubbleBarBubble updatedBubble;
        List<BubbleBarBubble> currentBubbles;

        BubbleBarViewUpdate(BubbleBarUpdate update) {
            initialState = update.initialState;
            expandedChanged = update.expandedChanged;
            expanded = update.expanded;
            shouldShowEducation = update.shouldShowEducation;
            selectedBubbleKey = update.selectedBubbleKey;
            suppressedBubbleKey = update.suppressedBubbleKey;
            unsuppressedBubbleKey = update.unsupressedBubbleKey;
            bubbleBarLocation = update.bubbleBarLocation;
            removedBubbles = update.removedBubbles;
            bubbleKeysInOrder = update.bubbleKeysInOrder;
            expandedViewDropTargetSize = update.expandedViewDropTargetSize;
            showOverflow = update.showOverflow;
            showOverflowChanged = update.showOverflowChanged;
        }
    }

    public BubbleBarController(Context context, BubbleBarView bubbleView) {
        mContext = context;
        mBarView = bubbleView; // Need the view for inflating bubble views.

        mSystemUiProxy = SystemUiProxy.INSTANCE.get(context);
    }

    public void onDestroy() {
        mSystemUiProxy.setBubblesListener(null);
        // Saves bubble bar state
        mSharedState.bubbleBarExpanded = mBubbleBarViewController.isExpanded();
        mSharedState.bubbleBarStashed = mBubbleStashController.isStashed();
        mSharedState.selectedBubbleKey = mSelectedBubble != null ? mSelectedBubble.getKey() : null;
        BubbleInfo[] bubbleInfoItems = new BubbleInfo[mBubbles.size()];
        mBubbles.values().forEach(bubbleBarBubble -> {
            int index = mBubbleBarViewController.bubbleViewIndex(bubbleBarBubble.getView());
            if (index < 0 || index >= bubbleInfoItems.length) {
                Log.e(TAG, "Found improper index: " + index + " for " + bubbleBarBubble);
            } else {
                bubbleInfoItems[index] = bubbleBarBubble.getInfo();
            }
        });
        mSharedState.bubbleInfoItems = Arrays.asList(bubbleInfoItems);
        mSharedState.suppressedBubbleInfoItems = new ArrayList<>(mSuppressedBubbles.size());
        for (int i = 0; i < mSuppressedBubbles.size(); i++) {
            mSharedState.suppressedBubbleInfoItems.add(mSuppressedBubbles.valueAt(i).getInfo());
        }
    }

    /** Initializes controllers. */
    public void init(BubbleControllers bubbleControllers,
            BubbleBarLocationListener bubbleBarLocationListener,
            TaskbarSharedState sharedState) {
        mSharedState = sharedState;
        mBubbleBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleStashController = bubbleControllers.bubbleStashController;
        mBubbleStashedHandleViewController = bubbleControllers.bubbleStashedHandleViewController;
        mBubblePinController = bubbleControllers.bubblePinController;
        mBubbleCreator = bubbleControllers.bubbleCreator;
        mBubbleBarLocationListener = bubbleBarLocationListener;

        bubbleControllers.runAfterInit(() -> {
            restoreSavedState(sharedState);
            mBubbleBarViewController.setHiddenForBubbles(
                    !sBubbleBarEnabled || mBubbles.isEmpty());
            mBubbleStashedHandleViewController.ifPresent(
                    controller -> controller.setHiddenForBubbles(
                            !sBubbleBarEnabled || mBubbles.isEmpty()));
            mBubbleBarViewController.setUpdateSelectedBubbleAfterCollapse(
                    key -> setSelectedBubbleInternal(mBubbles.get(key)));
            mBubbleBarViewController.setBoundsChangeListener(this::onBubbleBarBoundsChanged);
            mBubbleBarLocationListener.onBubbleBarLocationUpdated(
                    mBubbleBarViewController.getBubbleBarLocation());
            if (sBubbleBarEnabled) {
                mSystemUiProxy.setBubblesListener(this);
                mSystemUiProxy.setHasBubbleBar(true);
            }
        });
    }

    /**
     * Updates the bubble bar, handle bar, and stash controllers based on sysui state flags.
     */
    public void updateStateForSysuiFlags(@SystemUiStateFlags long flags) {
        boolean hideBubbleBar = (flags & MASK_HIDE_BUBBLE_BAR) != 0;
        mBubbleBarViewController.setHiddenForSysui(hideBubbleBar);

        boolean hideHandleView = (flags & MASK_HIDE_HANDLE_VIEW) != 0;
        mBubbleStashedHandleViewController.ifPresent(controller -> {
            controller.setHiddenForSysui(hideHandleView);
            MultiPropertyFactory<View>.MultiProperty handleViewAlpha =
                    mBubbleStashController.getHandleViewAlpha();
            boolean shouldShowHandleView = handleViewAlpha != null
                    && !hideHandleView
                    && mBubbleStashController.isStashed()
                    && mBubbleBarViewController.hasBubbles();
            if (shouldShowHandleView) {
                // TODO: (b/273592694) animate it?
                handleViewAlpha.setValue(1f);
            }
        });

        boolean sysuiLocked = (flags & MASK_SYSUI_LOCKED) != 0;
        mBubbleStashController.setSysuiLocked(sysuiLocked);
        mIsImeVisible = (flags & SYSUI_STATE_IME_VISIBLE) != 0;
        if (mIsImeVisible) {
            mBubbleBarViewController.onImeVisible();
        }
    }

    //
    // Bubble data changes
    //

    @BinderThread
    @Override
    public void onBubbleStateChange(Bundle bundle) {
        bundle.setClassLoader(BubbleBarUpdate.class.getClassLoader());
        BubbleBarUpdate update = bundle.getParcelable("update", BubbleBarUpdate.class);
        BubbleBarViewUpdate viewUpdate = new BubbleBarViewUpdate(update);
        if (update.addedBubble != null
                || update.updatedBubble != null
                || !update.currentBubbleList.isEmpty()) {
            // We have bubbles to load
            BUBBLE_STATE_EXECUTOR.execute(() -> {
                if (update.addedBubble != null) {
                    viewUpdate.addedBubble = mBubbleCreator.populateBubble(mContext,
                            update.addedBubble,
                            mBarView,
                            null /* existingBubble */);
                }
                if (update.updatedBubble != null) {
                    BubbleBarBubble existingBubble = mBubbles.get(update.updatedBubble.getKey());
                    viewUpdate.updatedBubble =
                            mBubbleCreator.populateBubble(mContext, update.updatedBubble,
                                    mBarView,
                                    existingBubble);
                }
                if (update.currentBubbleList != null && !update.currentBubbleList.isEmpty()) {
                    List<BubbleBarBubble> currentBubbles = new ArrayList<>();
                    for (int i = 0; i < update.currentBubbleList.size(); i++) {
                        BubbleBarBubble b = mBubbleCreator.populateBubble(mContext,
                                update.currentBubbleList.get(i), mBarView,
                                null /* existingBubble */);
                        currentBubbles.add(b);
                    }
                    viewUpdate.currentBubbles = currentBubbles;
                }
                MAIN_EXECUTOR.execute(() -> applyViewChanges(viewUpdate));
            });
        } else {
            // No bubbles to load, immediately apply the changes.
            BUBBLE_STATE_EXECUTOR.execute(
                    () -> MAIN_EXECUTOR.execute(() -> applyViewChanges(viewUpdate)));
        }
    }

    private void restoreSavedState(TaskbarSharedState sharedState) {
        if (sharedState.bubbleBarLocation != null) {
            updateBubbleBarLocationInternal(sharedState.bubbleBarLocation);
        }
        List<BubbleInfo> savedBubbles = sharedState.bubbleInfoItems;
        boolean hasSavedBubbles = savedBubbles != null && !savedBubbles.isEmpty();
        if (hasSavedBubbles) {
            restoreSavedBubbles(savedBubbles);
        }
        restoreSuppressed(sharedState.suppressedBubbleInfoItems);
        if (hasSavedBubbles) {
            setSelectedBubbleInternal(mBubbles.get(sharedState.selectedBubbleKey));
            if (sharedState.bubbleBarExpanded) {
                // We don't want state restore to have side effects which update the Shell state.
                // Use the method for setting expanded state from sysui as that won't trigger an
                // update back to Shell.
                mBubbleBarViewController.setExpandedFromSysui(/* isExpanded= */ true,
                        /* animate= */ false);
            } else if (sharedState.bubbleBarStashed) {
                mBubbleStashController.stashBubbleBarImmediate();
            } else {
                mBubbleStashController.showBubbleBarImmediate();
            }
        }
    }

    private void restoreSavedBubbles(List<BubbleInfo> bubbleInfos) {
        // Iterate in reverse because new bubbles are added in front and the list is in order.
        for (int i = bubbleInfos.size() - 1; i >= 0; i--) {
            BubbleBarBubble bubble = mBubbleCreator.populateBubble(mContext,
                    bubbleInfos.get(i), mBarView, /* existingBubble = */ null);
            if (bubble == null) {
                Log.e(TAG, "Could not instantiate BubbleBarBubble for " + bubbleInfos.get(i));
                continue;
            }
            mBubbles.put(bubble.getKey(), bubble);
            mBubbleBarViewController.restoreBubble(bubble);
        }
    }

    private void restoreSuppressed(List<BubbleInfo> bubbleInfos) {
        if (bubbleInfos == null || bubbleInfos.isEmpty()) return;
        for (BubbleInfo bubbleInfo : bubbleInfos.reversed()) {
            BubbleBarBubble bb = mBubbleCreator.populateBubble(mContext, bubbleInfo,
                    mBarView, /* existingBubble= */
                    null);
            if (bb != null) {
                mSuppressedBubbles.put(bb.getKey(), bb);
            }
        }
    }

    private void applyViewChanges(BubbleBarViewUpdate update) {
        if (update.initialState) {
            // it is possible that we tried to notify shell too early with the bubble bar bounds,
            // so force update shell about the bubble bar bounds in the initial handshake.
            onBubbleBarBoundsChanged(/* forceUpdate= */ true);
        }
        final boolean isCollapsed = (update.expandedChanged && !update.expanded)
                || (!update.expandedChanged && !mBubbleBarViewController.isExpanded());
        final boolean isExpanding = update.expandedChanged && update.expanded;
        // don't animate bubbles if this is the initial state because we may be unfolding or
        // enabling gesture nav. also suppress animation if the bubble bar is hidden for sysui e.g.
        // the shade is open, or we're locked.
        final boolean suppressAnimation =
                update.initialState || mBubbleBarViewController.isHiddenForSysui() || mIsImeVisible;

        if (update.initialState && mSharedState.hasSavedBubbles()) {
            // clear restored state
            mBubbleBarViewController.removeAllBubbles();
            mBubbles.clear();
            mBubbleBarViewController.showOverflow(update.showOverflow);
        }

        if (update.addedBubble != null) {
            mBubbles.put(update.addedBubble.getKey(), update.addedBubble);
        }
        BubbleBarBubble bubbleToSelect = null;
        if (update.selectedBubbleKey != null) {
            if (mSelectedBubble == null
                    || !update.selectedBubbleKey.equals(mSelectedBubble.getKey())) {
                BubbleBarBubble newlySelected = mBubbles.get(update.selectedBubbleKey);
                if (newlySelected != null) {
                    bubbleToSelect = newlySelected;
                }
            }
        }
        if (Flags.enableOptionalBubbleOverflow()
                && update.showOverflowChanged && !update.showOverflow && update.addedBubble != null
                && update.removedBubbles.isEmpty()
                && !mBubbles.isEmpty()) {
            // A bubble was added from the overflow (& now it's empty / not showing)
            mBubbleBarViewController.removeOverflowAndAddBubble(update.addedBubble, bubbleToSelect);
        } else if (update.addedBubble != null && update.removedBubbles.size() == 1) {
            // we're adding and removing a bubble at the same time. handle this as a single update.
            RemovedBubble removedBubble = update.removedBubbles.get(0);
            BubbleBarBubble bubbleToRemove = mBubbles.remove(removedBubble.getKey());
            boolean showOverflow = update.showOverflowChanged && update.showOverflow;
            if (bubbleToRemove != null) {
                mBubbleBarViewController.addBubbleAndRemoveBubble(update.addedBubble,
                        bubbleToRemove, bubbleToSelect, isExpanding, suppressAnimation,
                        showOverflow);
            } else {
                mBubbleBarViewController.addBubble(update.addedBubble, isExpanding,
                        suppressAnimation, bubbleToSelect);
                Log.w(TAG, "trying to remove bubble that doesn't exist: " + removedBubble.getKey());
            }
        } else {
            boolean overflowNeedsToBeAdded = Flags.enableOptionalBubbleOverflow()
                    && update.showOverflowChanged && update.showOverflow;
            if (!update.removedBubbles.isEmpty()) {
                for (int i = 0; i < update.removedBubbles.size(); i++) {
                    RemovedBubble removedBubble = update.removedBubbles.get(i);
                    BubbleBarBubble bubble = mBubbles.remove(removedBubble.getKey());
                    if (bubble != null && overflowNeedsToBeAdded) {
                        // First removal, show the overflow
                        overflowNeedsToBeAdded = false;
                        mBubbleBarViewController.addOverflowAndRemoveBubble(bubble, bubbleToSelect);
                    } else if (bubble != null) {
                        mBubbleBarViewController.removeBubble(bubble);
                    } else {
                        Log.w(TAG, "trying to remove bubble that doesn't exist: "
                                + removedBubble.getKey());
                    }
                }
            }
            if (update.addedBubble != null) {
                mBubbleBarViewController.addBubble(update.addedBubble, isExpanding,
                        suppressAnimation, bubbleToSelect);
            }
            if (Flags.enableOptionalBubbleOverflow()
                    && update.showOverflowChanged
                    && update.showOverflow != mBubbleBarViewController.isOverflowAdded()) {
                mBubbleBarViewController.showOverflow(update.showOverflow);
            }
        }

        // if a bubble was updated upstream, but removed before the update was received, add it back
        if (update.updatedBubble != null && !mBubbles.containsKey(update.updatedBubble.getKey())) {
            addBubbleInternally(update.updatedBubble, isExpanding, suppressAnimation);
        }

        if (update.addedBubble != null && isCollapsed && bubbleToSelect == null) {
            // If we're collapsed, the most recently added bubble will be selected.
            bubbleToSelect = update.addedBubble;
        }

        if (update.currentBubbles != null && !update.currentBubbles.isEmpty()) {
            // Iterate in reverse because new bubbles are added in front and the list is in order.
            for (int i = update.currentBubbles.size() - 1; i >= 0; i--) {
                BubbleBarBubble bubble = update.currentBubbles.get(i);
                if (bubble != null) {
                    if (bubble.getKey().equals(update.selectedBubbleKey)) {
                        bubbleToSelect = bubble;
                    }
                    addBubbleInternally(bubble, isExpanding, suppressAnimation);
                    if (isCollapsed && bubbleToSelect == null) {
                        // If we're collapsed, the most recently added bubble will be selected.
                        bubbleToSelect = bubble;
                    }
                } else {
                    Log.w(TAG, "trying to add bubble but null after loading! "
                            + update.addedBubble.getKey());
                }
            }
        }
        if (Flags.enableOptionalBubbleOverflow() && update.initialState && update.showOverflow) {
            mBubbleBarViewController.showOverflow(true);
        }

        if (update.suppressedBubbleKey != null) {
            BubbleBarBubble bb = mBubbles.remove(update.suppressedBubbleKey);
            if (bb != null) {
                mSuppressedBubbles.put(update.suppressedBubbleKey, bb);
                mBubbleBarViewController.removeBubble(bb);
            }
        }
        if (update.unsuppressedBubbleKey != null) {
            BubbleBarBubble bb = mSuppressedBubbles.remove(update.unsuppressedBubbleKey);
            if (bb != null) {
                // Unsuppressing an existing bubble should not cause the bar to expand or animate
                addBubbleInternally(bb, /* isExpanding= */ false, /* suppressAnimation= */ true);
                if (mBubbleBarViewController.isHiddenForNoBubbles()) {
                    mBubbleBarViewController.setHiddenForBubbles(false);
                }
            }
        }

        // Update the visibility if this is the initial state, if there are no bubbles, or if the
        // animation is suppressed.
        // If this is the initial bubble, the bubble bar will become visible as part of the
        // animation.
        if (update.initialState || mBubbles.isEmpty() || suppressAnimation) {
            mBubbleBarViewController.setHiddenForBubbles(mBubbles.isEmpty());
        }
        mBubbleStashedHandleViewController.ifPresent(
                controller -> controller.setHiddenForBubbles(mBubbles.isEmpty()));

        if (mBubbles.isEmpty()) {
            // all bubbles were removed. clear the selected bubble
            mSelectedBubble = null;
        }

        if (update.updatedBubble != null) {
            // Updates mean the dot state may have changed; any other changes were updated in
            // the populateBubble step.
            BubbleBarBubble bb = mBubbles.get(update.updatedBubble.getKey());
            if (suppressAnimation) {
                // since we're not animating this update, we should update the dot visibility here.
                bb.getView().updateDotVisibility(/* animate= */ false);
            } else {
                mBubbleBarViewController.animateBubbleNotification(
                        bb, /* isExpanding= */ false, /* isUpdate= */ true);
            }
        }
        if (update.bubbleKeysInOrder != null && !update.bubbleKeysInOrder.isEmpty()) {
            // Create the new list
            List<BubbleBarBubble> newOrder = update.bubbleKeysInOrder.stream()
                    .map(mBubbles::get).filter(Objects::nonNull).toList();
            if (!newOrder.isEmpty()) {
                mBubbleBarViewController.reorderBubbles(newOrder);
            }
        }
        if (bubbleToSelect != null) {
            setSelectedBubbleInternal(bubbleToSelect);
        } else if (update.initialState && BubbleBarOverflow.KEY.equals(update.selectedBubbleKey)) {
            // this is the initial update with the overflow selected which could happen after
            // unfolding with the overflow expanded
            setSelectedBubbleInternal(mBubbleBarViewController.getOverflowBubble());
        }
        if (update.shouldShowEducation) {
            mBubbleBarViewController.prepareToShowEducation();
        }
        if (update.expandedChanged) {
            if (update.expanded != mBubbleBarViewController.isExpanded()) {
                // If we start as expanded, show bar immediately without waiting for animation.
                boolean animate = !update.initialState;
                mBubbleBarViewController.setExpandedFromSysui(update.expanded, animate);
            } else {
                Log.w(TAG, "expansion was changed but is the same");
            }
        }
        if (update.bubbleBarLocation != null) {
            mSharedState.bubbleBarLocation = update.bubbleBarLocation;
            if (update.bubbleBarLocation != mBubbleBarViewController.getBubbleBarLocation()) {
                updateBubbleBarLocationInternal(update.bubbleBarLocation);
            }
        }
        if (update.expandedViewDropTargetSize != null) {
            mBubblePinController.setDropTargetSize(update.expandedViewDropTargetSize);
        }
    }

    /**
     * Removes the given bubble from the backing list of bubbles after it was dismissed by the user.
     */
    public void onBubbleDismissed(BubbleView bubble) {
        mBubbles.remove(bubble.getBubble().getKey());
    }

    /** Tells WMShell to show the currently selected bubble. */
    public void showSelectedBubble() {
        if (getSelectedBubbleKey() != null) {
            mLastSentBubbleBarTopToScreenBottom = mBarView.getTopToScreenBottom();
            mSystemUiProxy.showBubble(getSelectedBubbleKey(), mLastSentBubbleBarTopToScreenBottom);
        } else {
            Log.w(TAG, "Trying to show the selected bubble but it's null");
        }
    }

    /** Updates the currently selected bubble for launcher views and tells WMShell to show it. */
    public void showAndSelectBubble(BubbleBarItem b) {
        if (DEBUG) Log.w(TAG, "showingSelectedBubble: " + b.getKey());
        setSelectedBubbleInternal(b);
        showSelectedBubble();
    }

    /**
     * Sets the bubble that should be selected. This notifies the views, it does not notify
     * WMShell that the selection has changed, that should go through either
     * {@link #showSelectedBubble()} or {@link #showAndSelectBubble(BubbleBarItem)}.
     */
    private void setSelectedBubbleInternal(BubbleBarItem b) {
        if (!Objects.equals(b, mSelectedBubble)) {
            if (DEBUG) Log.w(TAG, "selectingBubble: " + b.getKey());
            mSelectedBubble = b;
            mBubbleBarViewController.updateSelectedBubble(mSelectedBubble);
        }
    }

    /**
     * Returns the selected bubble or null if no bubble is selected.
     */
    @Nullable
    public String getSelectedBubbleKey() {
        if (mSelectedBubble != null) {
            return mSelectedBubble.getKey();
        }
        return null;
    }

    /**
     * Set a new bubble bar location.
     * <p>
     * Updates the value locally in Launcher and in WMShell.
     */
    public void updateBubbleBarLocation(BubbleBarLocation location,
            @BubbleBarLocation.UpdateSource int source) {
        updateBubbleBarLocationInternal(location);
        mSystemUiProxy.setBubbleBarLocation(location, source);
    }

    private void updateBubbleBarLocationInternal(BubbleBarLocation location) {
        mBubbleBarViewController.setBubbleBarLocation(location);
        mBubbleStashController.setBubbleBarLocation(location);
        mBubbleBarLocationListener.onBubbleBarLocationUpdated(location);
    }

    @Override
    public void animateBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        MAIN_EXECUTOR.execute(
                () -> {
                    mBubbleBarViewController.animateBubbleBarLocation(bubbleBarLocation);
                    mBubbleBarLocationListener.onBubbleBarLocationAnimated(bubbleBarLocation);
                });
    }

    @Override
    public void showBubbleBarPillowAt(@Nullable BubbleBarLocation location) {
        MAIN_EXECUTOR.execute(() -> {
            //TODO(b/411505605) add logic to show pillow and update taskbar
        });
    }

    /** Notifies WMShell to show the expanded view. */
    void showExpandedView() {
        mSystemUiProxy.showExpandedView();
    }

    //
    // Loading data for the bubbles
    //

    private void onBubbleBarBoundsChanged() {
        onBubbleBarBoundsChanged(/* forceUpdate= */ false);
    }

    private void onBubbleBarBoundsChanged(boolean forceUpdate) {
        int bubbleBarTopToScreenBottom = mBarView.getTopToScreenBottom();
        if (bubbleBarTopToScreenBottom != mLastSentBubbleBarTopToScreenBottom || forceUpdate) {
            mLastSentBubbleBarTopToScreenBottom = bubbleBarTopToScreenBottom;
            mSystemUiProxy.updateBubbleBarTopToScreenBottom(bubbleBarTopToScreenBottom);
        }
    }

    private void addBubbleInternally(BubbleBarBubble bubble, boolean isExpanding,
            boolean suppressAnimation) {
        mBubbles.put(bubble.getKey(), bubble);
        mBubbleBarViewController.addBubble(bubble, isExpanding,
                suppressAnimation, /* bubbleToSelect = */ null);
    }

    /** Listener of {@link BubbleBarLocation} updates. */
    public interface BubbleBarLocationListener {

        /** Called when {@link BubbleBarLocation} is animated, but change is not yet final. */
        void onBubbleBarLocationAnimated(BubbleBarLocation location);

        /** Called when {@link BubbleBarLocation} is updated permanently. */
        void onBubbleBarLocationUpdated(BubbleBarLocation location);
    }
}
