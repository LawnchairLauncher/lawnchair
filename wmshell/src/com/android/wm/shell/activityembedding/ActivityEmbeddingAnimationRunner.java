/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.activityembedding;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManagerPolicyConstants.TYPE_LAYER_OFFSET;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.wm.shell.activityembedding.ActivityEmbeddingAnimationSpec.createShowSnapshotForClosingAnimation;
import static com.android.wm.shell.transition.TransitionAnimationHelper.addBackgroundToTransition;
import static com.android.wm.shell.transition.TransitionAnimationHelper.edgeExtendWindow;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionBackgroundColorIfSet;
import static com.android.wm.shell.transition.Transitions.TRANSIT_TASK_FRAGMENT_DRAG_RESIZE;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.ArraySet;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.activityembedding.ActivityEmbeddingAnimationAdapter.SnapshotAdapter;
import com.android.wm.shell.common.ScreenshotUtils;
import com.android.wm.shell.shared.TransitionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** To run the ActivityEmbedding animations. */
class ActivityEmbeddingAnimationRunner {

    private static final String TAG = "ActivityEmbeddingAnimR";

    private final ActivityEmbeddingController mController;
    @VisibleForTesting
    final ActivityEmbeddingAnimationSpec mAnimationSpec;

    @Nullable
    private Animator mActiveAnimator;

    ActivityEmbeddingAnimationRunner(@NonNull Context context,
            @NonNull ActivityEmbeddingController controller) {
        mController = controller;
        mAnimationSpec = new ActivityEmbeddingAnimationSpec(context);
    }

    /** Creates and starts animation for ActivityEmbedding transition. */
    void startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        // There may be some surface change that we want to apply after the start transaction is
        // applied to make sure the surface is ready.
        final List<Consumer<SurfaceControl.Transaction>> postStartTransactionCallbacks =
                new ArrayList<>();
        final Animator animator = createAnimator(info, startTransaction,
                finishTransaction,
                () -> mController.onAnimationFinished(transition), postStartTransactionCallbacks);
        mActiveAnimator = animator;

        // Start the animation.
        if (!postStartTransactionCallbacks.isEmpty()) {
            // postStartTransactionCallbacks require that the start transaction is already
            // applied to run otherwise they may result in flickers and UI inconsistencies.
            startTransaction.apply(true /* sync */);

            // Run tasks that require startTransaction to already be applied
            final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            for (Consumer<SurfaceControl.Transaction> postStartTransactionCallback :
                    postStartTransactionCallbacks) {
                postStartTransactionCallback.accept(t);
            }
            t.apply();
            animator.start();
        } else {
            startTransaction.apply();
            animator.start();
        }
    }

    void cancelAnimationFromMerge() {
        if (mActiveAnimator == null) {
            Log.e(TAG,
                    "No active ActivityEmbedding animator running but mergeAnimation is "
                            + "trying to cancel one."
            );
            return;
        }
        mActiveAnimator.end();
    }

    /**
     * Sets transition animation scale settings value.
     * @param scale The setting value of transition animation scale.
     */
    void setAnimScaleSetting(float scale) {
        mAnimationSpec.setAnimScaleSetting(scale);
    }

    /** Creates the animator for the given {@link TransitionInfo}. */
    @VisibleForTesting
    @NonNull
    Animator createAnimator(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Runnable animationFinishCallback,
            @NonNull List<Consumer<SurfaceControl.Transaction>> postStartTransactionCallbacks) {
        final List<ActivityEmbeddingAnimationAdapter> adapters = createAnimationAdapters(info,
                startTransaction);
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        long duration = 0;
        if (adapters.isEmpty()) {
            // Jump cut
            // No need to modify the animator, but to update the startTransaction with the changes'
            // ending states.
            prepareForJumpCut(info, startTransaction);
        } else {
            addEdgeExtensionIfNeeded(startTransaction, finishTransaction,
                    postStartTransactionCallbacks, adapters);
            addBackgroundColorIfNeeded(info, startTransaction, finishTransaction, adapters);
            for (ActivityEmbeddingAnimationAdapter adapter : adapters) {
                duration = Math.max(duration, adapter.getDurationHint());
            }
            animator.addUpdateListener((anim) -> {
                // Update all adapters in the same transaction.
                final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
                for (ActivityEmbeddingAnimationAdapter adapter : adapters) {
                    adapter.onAnimationUpdate(t, animator.getCurrentPlayTime());
                }
                t.apply();
            });
            prepareForFirstFrame(startTransaction, adapters);
        }
        animator.setDuration(duration);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
                final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                for (ActivityEmbeddingAnimationAdapter adapter : adapters) {
                    adapter.onAnimationEnd(t);
                }
                t.apply();
                mActiveAnimator = null;
                animationFinishCallback.run();
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
        return animator;
    }

    /**
     * Creates list of {@link ActivityEmbeddingAnimationAdapter} to handle animations on all window
     * changes.
     */
    @NonNull
    private List<ActivityEmbeddingAnimationAdapter> createAnimationAdapters(
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startTransaction) {
        if (info.getType() == TRANSIT_TASK_FRAGMENT_DRAG_RESIZE) {
            // Jump cut for AE drag resizing because the content is veiled.
            return new ArrayList<>();
        }
        boolean isChangeTransition = false;
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.hasFlags(FLAG_IS_BEHIND_STARTING_WINDOW)) {
                // Skip the animation if the windows are behind an app starting window.
                return new ArrayList<>();
            }
            if (!isChangeTransition && change.getMode() == TRANSIT_CHANGE
                    && !change.getStartAbsBounds().equals(change.getEndAbsBounds())) {
                isChangeTransition = true;
            }
        }
        if (isChangeTransition) {
            return createChangeAnimationAdapters(info, startTransaction);
        }
        if (TransitionUtil.isClosingType(info.getType())) {
            return createCloseAnimationAdapters(info, startTransaction);
        }
        return createOpenAnimationAdapters(info, startTransaction);
    }

    @NonNull
    private List<ActivityEmbeddingAnimationAdapter> createOpenAnimationAdapters(
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startTransaction) {
        return createOpenCloseAnimationAdapters(info, true /* isOpening */,
                mAnimationSpec::loadOpenAnimation, startTransaction);
    }

    @NonNull
    private List<ActivityEmbeddingAnimationAdapter> createCloseAnimationAdapters(
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startTransaction) {
        return createOpenCloseAnimationAdapters(info, false /* isOpening */,
                mAnimationSpec::loadCloseAnimation, startTransaction);
    }

    /**
     * Creates {@link ActivityEmbeddingAnimationAdapter} for OPEN and CLOSE types of transition.
     * @param isOpening {@code true} for OPEN type, {@code false} for CLOSE type.
     */
    @NonNull
    private List<ActivityEmbeddingAnimationAdapter> createOpenCloseAnimationAdapters(
            @NonNull TransitionInfo info, boolean isOpening,
            @NonNull AnimationProvider animationProvider,
            @NonNull SurfaceControl.Transaction startTransaction) {
        // We need to know if the change window is only a partial of the whole animation screen.
        // If so, we will need to adjust it to make the whole animation screen looks like one.
        final List<TransitionInfo.Change> openingChanges = new ArrayList<>();
        final List<TransitionInfo.Change> closingChanges = new ArrayList<>();
        final Rect openingWholeScreenBounds = new Rect();
        final Rect closingWholeScreenBounds = new Rect();
        for (TransitionInfo.Change change : info.getChanges()) {
            if (TransitionUtil.isOpeningType(change.getMode())) {
                openingChanges.add(change);
                openingWholeScreenBounds.union(change.getEndAbsBounds());
            } else {
                closingChanges.add(change);
                // Also union with the start bounds because the closing transition may be shrunk.
                closingWholeScreenBounds.union(change.getStartAbsBounds());
                closingWholeScreenBounds.union(change.getEndAbsBounds());
            }
        }

        // For OPEN transition, open windows should be above close windows.
        // For CLOSE transition, open windows should be below close windows.
        int offsetLayer = TYPE_LAYER_OFFSET;
        final List<ActivityEmbeddingAnimationAdapter> adapters = new ArrayList<>();
        for (TransitionInfo.Change change : openingChanges) {
            final Animation animation =
                    animationProvider.get(info, change, openingWholeScreenBounds);
            if (animation.getDuration() == 0) {
                continue;
            }
            final ActivityEmbeddingAnimationAdapter adapter = createOpenCloseAnimationAdapter(
                    info, change, animation, openingWholeScreenBounds);
            if (isOpening) {
                adapter.overrideLayer(offsetLayer++);
            }
            adapters.add(adapter);
        }
        for (TransitionInfo.Change change : closingChanges) {
            if (shouldUseSnapshotAnimationForClosingChange(change)) {
                SurfaceControl screenshot = getOrCreateScreenshot(change, change, startTransaction);
                if (screenshot != null) {
                    final SnapshotAdapter snapshotAdapter = new SnapshotAdapter(
                            createShowSnapshotForClosingAnimation(), change, screenshot,
                            TransitionUtil.getRootFor(change, info));
                    if (!isOpening) {
                        snapshotAdapter.overrideLayer(offsetLayer++);
                    }
                    adapters.add(snapshotAdapter);
                }
            }
            final Animation animation =
                    animationProvider.get(info, change, closingWholeScreenBounds);
            if (animation.getDuration() == 0) {
                continue;
            }
            final ActivityEmbeddingAnimationAdapter adapter = createOpenCloseAnimationAdapter(
                    info, change, animation, closingWholeScreenBounds);
            if (!isOpening) {
                adapter.overrideLayer(offsetLayer++);
            }
            adapters.add(adapter);
        }
        return adapters;
    }

    /**
     * Returns whether we should use snapshot animation for the closing change.
     * It's usually because the end bounds of the closing change are shrunk, which leaves a black
     * area in the transition.
     */
    static boolean shouldUseSnapshotAnimationForClosingChange(
            @NonNull TransitionInfo.Change closingChange) {
        // Only check closing type because we only take screenshot for closing bounds-changing
        // changes.
        if (!TransitionUtil.isClosingType(closingChange.getMode())) {
            return false;
        }
        // Don't need to take screenshot if there's no bounds change.
        return !closingChange.getStartAbsBounds().equals(closingChange.getEndAbsBounds());
    }

    /** Sets the first frame to the {@code startTransaction} to avoid any flicker on start. */
    private void prepareForFirstFrame(@NonNull SurfaceControl.Transaction startTransaction,
            @NonNull List<ActivityEmbeddingAnimationAdapter> adapters) {
        startTransaction.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        for (ActivityEmbeddingAnimationAdapter adapter : adapters) {
            adapter.prepareForFirstFrame(startTransaction);
        }
    }

    /** Adds edge extension to the surfaces that have such an animation property. */
    private void addEdgeExtensionIfNeeded(@NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull List<Consumer<SurfaceControl.Transaction>> postStartTransactionCallbacks,
            @NonNull List<ActivityEmbeddingAnimationAdapter> adapters) {
        for (ActivityEmbeddingAnimationAdapter adapter : adapters) {
            final Animation animation = adapter.mAnimation;
            if (!animation.hasExtension()) {
                continue;
            }
            if (adapter.mChange.hasFlags(FLAG_TRANSLUCENT)
                    && adapter.mChange.getActivityComponent() != null) {
                // Skip edge extension for translucent activity.
                continue;
            }
            final TransitionInfo.Change change = adapter.mChange;
            if (TransitionUtil.isOpeningType(adapter.mChange.getMode())) {
                // Need to screenshot after startTransaction is applied otherwise activity
                // may not be visible or ready yet.
                postStartTransactionCallbacks.add(
                        t -> edgeExtendWindow(change, animation, t, finishTransaction));
            } else {
                // Can screenshot now (before startTransaction is applied)
                edgeExtendWindow(change, animation, startTransaction, finishTransaction);
            }
        }
    }

    /** Adds background color to the transition if any animation has such a property. */
    private void addBackgroundColorIfNeeded(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull List<ActivityEmbeddingAnimationAdapter> adapters) {
        for (ActivityEmbeddingAnimationAdapter adapter : adapters) {
            final int backgroundColor = getTransitionBackgroundColorIfSet(info, adapter.mChange,
                    adapter.mAnimation, 0 /* defaultColor */);
            if (backgroundColor != 0) {
                // We only need to show one color.
                addBackgroundToTransition(info.getRootLeash(), backgroundColor, startTransaction,
                        finishTransaction);
                return;
            }
        }
    }

    @NonNull
    private ActivityEmbeddingAnimationAdapter createOpenCloseAnimationAdapter(
            @NonNull TransitionInfo info, @NonNull TransitionInfo.Change change,
            @NonNull Animation animation, @NonNull Rect wholeAnimationBounds) {
        return new ActivityEmbeddingAnimationAdapter(animation, change, change.getLeash(),
                wholeAnimationBounds, TransitionUtil.getRootFor(change, info));
    }

    @NonNull
    private List<ActivityEmbeddingAnimationAdapter> createChangeAnimationAdapters(
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startTransaction) {
        if (shouldUseJumpCutForChangeTransition(info)) {
            return new ArrayList<>();
        }

        final List<ActivityEmbeddingAnimationAdapter> adapters = new ArrayList<>();
        final Set<TransitionInfo.Change> handledChanges = new ArraySet<>();

        // For the first iteration, we prepare the animation for the change type windows. This is
        // needed because there may be window that is reparented while resizing. In such case, we
        // will do the following:
        // 1. Capture a screenshot from the Activity surface.
        // 2. Attach the screenshot surface to the top of TaskFragment (Activity's parent) surface.
        // 3. Animate the TaskFragment using Activity Change info (start/end bounds).
        // This is because the TaskFragment surface/change won't contain the Activity's before its
        // reparent.
        Animation changeAnimation = null;
        Rect parentBounds = new Rect();
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getMode() != TRANSIT_CHANGE
                    || change.getStartAbsBounds().equals(change.getEndAbsBounds())) {
                continue;
            }

            // This is the window with bounds change.
            handledChanges.add(change);
            final WindowContainerToken parentToken = change.getParent();
            TransitionInfo.Change boundsAnimationChange = change;
            if (parentToken != null) {
                // When the parent window is also included in the transition as an opening window,
                // we would like to animate the parent window instead.
                final TransitionInfo.Change parentChange = info.getChange(parentToken);
                if (parentChange != null && TransitionUtil.isOpeningType(parentChange.getMode())) {
                    // We won't create a separate animation for the parent, but to animate the
                    // parent for the child resizing.
                    handledChanges.add(parentChange);
                    boundsAnimationChange = parentChange;
                }
            }

            // The TaskFragment may be enter/exit split, so we take the union of both as the parent
            // size.
            parentBounds.union(boundsAnimationChange.getStartAbsBounds());
            parentBounds.union(boundsAnimationChange.getEndAbsBounds());
            if (boundsAnimationChange != change) {
                // Union the change starting bounds in case the activity is resized and reparented
                // to a TaskFragment. In that case, the TaskFragment may not cover the activity's
                // starting bounds.
                parentBounds.union(change.getStartAbsBounds());
            }

            // There are two animations in the array. The first one is for the start leash
            // (snapshot), and the second one is for the end leash (TaskFragment).
            final Animation[] animations = mAnimationSpec.createChangeBoundsChangeAnimations(change,
                    parentBounds);
            // Keep track as we might need to add background color for the animation.
            // Although there may be multiple change animation, record one of them is sufficient
            // because the background color will be added to the root leash for the whole animation.
            changeAnimation = animations[1];

            // Create a screenshot based on change, but attach it to the top of the
            // boundsAnimationChange.
            final SurfaceControl screenshotLeash = getOrCreateScreenshot(change,
                    boundsAnimationChange, startTransaction);
            final TransitionInfo.Root root = TransitionUtil.getRootFor(change, info);
            if (screenshotLeash != null) {
                // Adapter for the starting screenshot leash.
                // The screenshot leash will be removed in SnapshotAdapter#onAnimationEnd
                adapters.add(new ActivityEmbeddingAnimationAdapter.SnapshotAdapter(
                        animations[0], change, screenshotLeash, root));
            } else {
                Log.e(TAG, "Failed to take screenshot for change=" + change);
            }
            // Adapter for the ending bounds changed leash.
            adapters.add(new ActivityEmbeddingAnimationAdapter.BoundsChangeAdapter(
                    animations[1], boundsAnimationChange, root));
        }

        if (parentBounds.isEmpty()) {
            throw new IllegalStateException(
                    "There should be at least one changing window to play the change animation");
        }

        // If there is no corresponding open/close window with the change, we should show background
        // color to cover the empty part of the screen.
        boolean shouldShouldBackgroundColor = true;
        // Handle the other windows that don't have bounds change in the same transition.
        for (TransitionInfo.Change change : info.getChanges()) {
            if (handledChanges.contains(change)) {
                // Skip windows that we have already handled in the previous iteration.
                continue;
            }

            final Animation animation;
            if ((change.getParent() != null
                    && handledChanges.contains(info.getChange(change.getParent())))
                    || change.getMode() == TRANSIT_CHANGE) {
                // No-op if it will be covered by the changing parent window, or it is a changing
                // window without bounds change.
                animation = ActivityEmbeddingAnimationSpec.createNoopAnimation(change);
            } else if (TransitionUtil.isClosingType(change.getMode())) {
                animation = mAnimationSpec.createChangeBoundsCloseAnimation(change, parentBounds);
                shouldShouldBackgroundColor = false;
            } else {
                animation = mAnimationSpec.createChangeBoundsOpenAnimation(change, parentBounds);
                shouldShouldBackgroundColor = false;
            }
            adapters.add(new ActivityEmbeddingAnimationAdapter(animation, change,
                    TransitionUtil.getRootFor(change, info)));
        }

        if (shouldShouldBackgroundColor && changeAnimation != null) {
            // Change animation may leave part of the screen empty. Show background color to cover
            // that.
            changeAnimation.setShowBackdrop(true);
        }

        return adapters;
    }

    /**
     * Takes a screenshot of the given {@code screenshotChange} surface if WM Core hasn't taken one.
     * The screenshot leash should be attached to the {@code animationChange} surface which we will
     * animate later.
     */
    @Nullable
    private SurfaceControl getOrCreateScreenshot(@NonNull TransitionInfo.Change screenshotChange,
            @NonNull TransitionInfo.Change animationChange,
            @NonNull SurfaceControl.Transaction t) {
        final SurfaceControl screenshotLeash = screenshotChange.getSnapshot();
        if (screenshotLeash != null) {
            // If WM Core has already taken a screenshot, make sure it is reparented to the
            // animation leash.
            t.reparent(screenshotLeash, animationChange.getLeash());
            return screenshotLeash;
        }

        // If WM Core hasn't taken a screenshot, take a screenshot now.
        final Rect cropBounds = new Rect(screenshotChange.getStartAbsBounds());
        cropBounds.offsetTo(0, 0);
        return ScreenshotUtils.takeScreenshot(t, screenshotChange.getLeash(),
                animationChange.getLeash(), cropBounds, Integer.MAX_VALUE);
    }

    /**
     * Whether we should use jump cut for the change transition.
     * This normally happens when opening a new secondary with the existing primary using a
     * different split layout (ratio or direction). This can be complicated, like from horizontal to
     * vertical split with new split pairs.
     * Uses a jump cut animation to simplify.
     */
    private boolean shouldUseJumpCutForChangeTransition(@NonNull TransitionInfo info) {
        // There can be reparenting of changing Activity to new open TaskFragment, so we need to
        // exclude both in the first iteration.
        final List<TransitionInfo.Change> changingChanges = new ArrayList<>();
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getMode() != TRANSIT_CHANGE
                    || change.getStartAbsBounds().equals(change.getEndAbsBounds())) {
                continue;
            }
            changingChanges.add(change);
            final WindowContainerToken parentToken = change.getParent();
            if (parentToken != null) {
                // When the parent window is also included in the transition as an opening window,
                // we would like to animate the parent window instead.
                final TransitionInfo.Change parentChange = info.getChange(parentToken);
                if (parentChange != null && TransitionUtil.isOpeningType(parentChange.getMode())) {
                    changingChanges.add(parentChange);
                }
            }
        }
        if (changingChanges.isEmpty()) {
            // No changing target found.
            return true;
        }

        // Check if the transition contains both opening and closing windows.
        final List<TransitionInfo.Change> openChanges = new ArrayList<>();
        final List<TransitionInfo.Change> closeChanges = new ArrayList<>();
        for (TransitionInfo.Change change : info.getChanges()) {
            if (changingChanges.contains(change)) {
                continue;
            }
            if (change.getParent() != null
                    && changingChanges.contains(info.getChange(change.getParent()))) {
                // No-op if it will be covered by the changing parent window.
                continue;
            }
            if (TransitionUtil.isOpeningType(change.getMode())) {
                openChanges.add(change);
            } else if (TransitionUtil.isClosingType(change.getMode())) {
                closeChanges.add(change);
            }
        }
        if (openChanges.isEmpty() || closeChanges.isEmpty()) {
            // Only skip if the transition contains both open and close.
            return false;
        }
        if (changingChanges.size() != 1 || openChanges.size() != 1 || closeChanges.size() != 1) {
            // Skip when there are too many windows involved.
            return true;
        }
        final TransitionInfo.Change changingChange = changingChanges.get(0);
        final TransitionInfo.Change openChange = openChanges.get(0);
        final TransitionInfo.Change closeChange = closeChanges.get(0);
        if (changingChange.getStartAbsBounds().equals(openChange.getEndAbsBounds())
                && changingChange.getEndAbsBounds().equals(closeChange.getStartAbsBounds())) {
            // Don't skip if the transition is a simple shifting without split direction or ratio
            // change. For example, A|B -> B|C.
            return false;
        }
        return true;
    }

    /** Updates the changes to end states in {@code startTransaction} for jump cut animation. */
    private void prepareForJumpCut(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction) {
        for (TransitionInfo.Change change : info.getChanges()) {
            final SurfaceControl leash = change.getLeash();
            if (change.getParent() != null) {
                startTransaction.setPosition(leash,
                        change.getEndRelOffset().x, change.getEndRelOffset().y);
            } else {
                // Change leash has been reparented to the root if its parent is not in the
                // transition.
                // Because it is reparented to the root, the actual offset should be its relative
                // position to the root instead. See Transitions#setupAnimHierarchy.
                final TransitionInfo.Root root = TransitionUtil.getRootFor(change, info);
                startTransaction.setPosition(leash,
                        change.getEndAbsBounds().left - root.getOffset().x,
                        change.getEndAbsBounds().top - root.getOffset().y);
            }
            startTransaction.setWindowCrop(leash,
                    change.getEndAbsBounds().width(), change.getEndAbsBounds().height());
            if (change.getMode() == TRANSIT_CLOSE) {
                startTransaction.hide(leash);
            } else {
                startTransaction.show(leash);
                startTransaction.setAlpha(leash, 1f);
            }
        }
    }

    /** To provide an {@link Animation} based on the transition infos. */
    private interface AnimationProvider {
        Animation get(@NonNull TransitionInfo info, @NonNull TransitionInfo.Change change,
                @NonNull Rect animationBounds);
    }
}
