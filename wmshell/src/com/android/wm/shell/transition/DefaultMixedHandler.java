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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;

import static com.android.wm.shell.shared.TransitionUtil.isOpeningType;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Pair;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.common.split.SplitScreenUtils;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.splitscreen.StageCoordinator;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A handler for dealing with transitions involving multiple other handlers. For example: an
 * activity in split-screen going into PiP. Note this is provided as a handset-specific
 * implementation of {@code MixedTransitionHandler}.
 */
public class DefaultMixedHandler implements MixedTransitionHandler,
        RecentsTransitionHandler.RecentsMixedHandler {

    private final Transitions mPlayer;
    private PipTransitionController mPipHandler;
    private RecentsTransitionHandler mRecentsHandler;
    private StageCoordinator mSplitHandler;
    private final KeyguardTransitionHandler mKeyguardHandler;
    private DesktopTasksController mDesktopTasksController;
    private UnfoldTransitionHandler mUnfoldHandler;
    private ActivityEmbeddingController mActivityEmbeddingController;

    abstract static class MixedTransition {
        /** Entering Pip from split, breaks split. */
        static final int TYPE_ENTER_PIP_FROM_SPLIT = 1;

        /** Both the display and split-state (enter/exit) is changing */
        static final int TYPE_DISPLAY_AND_SPLIT_CHANGE = 2;

        /** Pip was entered while handling an intent with its own remoteTransition. */
        static final int TYPE_OPTIONS_REMOTE_AND_PIP_CHANGE = 3;

        /** Recents transition while split-screen foreground. */
        static final int TYPE_RECENTS_DURING_SPLIT = 4;

        /** Keyguard exit/occlude/unocclude transition. */
        static final int TYPE_KEYGUARD = 5;

        /** Recents transition on top of the lock screen. */
        static final int TYPE_RECENTS_DURING_KEYGUARD = 6;

        /** Recents Transition while in desktop mode. */
        static final int TYPE_RECENTS_DURING_DESKTOP = 7;

        /** Fold/Unfold transition. */
        static final int TYPE_UNFOLD = 8;

        /** Enter pip from one of the Activity Embedding windows. */
        static final int TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING = 9;

        /** Entering Pip from split, but replace the Pip stage instead of breaking split. */
        static final int TYPE_ENTER_PIP_REPLACE_FROM_SPLIT = 10;

        /** The display changes when pip is entering. */
        static final int TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE = 11;

        /** The default animation for this mixed transition. */
        static final int ANIM_TYPE_DEFAULT = 0;

        /** For ENTER_PIP_FROM_SPLIT, indicates that this is a to-home animation. */
        static final int ANIM_TYPE_GOING_HOME = 1;

        /** For RECENTS_DURING_SPLIT, is set when this turns into a pair->pair task switch. */
        static final int ANIM_TYPE_PAIR_TO_PAIR = 1;

        final int mType;
        int mAnimType = ANIM_TYPE_DEFAULT;
        final IBinder mTransition;

        protected final Transitions mPlayer;
        protected final MixedTransitionHandler mMixedHandler;
        protected final PipTransitionController mPipHandler;
        protected final StageCoordinator mSplitHandler;
        protected final KeyguardTransitionHandler mKeyguardHandler;

        Transitions.TransitionHandler mLeftoversHandler = null;
        TransitionInfo mInfo = null;
        WindowContainerTransaction mFinishWCT = null;
        SurfaceControl.Transaction mFinishT = null;
        Transitions.TransitionFinishCallback mFinishCB = null;

        /**
         * Whether the transition has request for remote transition while mLeftoversHandler
         * isn't remote transition handler.
         * If true and the mLeftoversHandler can handle the transition, need to notify remote
         * transition handler to consume the transition.
         */
        boolean mHasRequestToRemote;

        /**
         * Mixed transitions are made up of multiple "parts". This keeps track of how many
         * parts are currently animating.
         */
        int mInFlightSubAnimations = 0;

        MixedTransition(int type, IBinder transition, Transitions player,
                MixedTransitionHandler mixedHandler, PipTransitionController pipHandler,
                StageCoordinator splitHandler, KeyguardTransitionHandler keyguardHandler) {
            mType = type;
            mTransition = transition;
            mPlayer = player;
            mMixedHandler = mixedHandler;
            mPipHandler = pipHandler;
            mSplitHandler = splitHandler;
            mKeyguardHandler = keyguardHandler;
        }

        abstract boolean startAnimation(
                @NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback);

        abstract void mergeAnimation(
                @NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback);

        abstract void onTransitionConsumed(
                @NonNull IBinder transition, boolean aborted,
                @Nullable SurfaceControl.Transaction finishT);

        protected boolean startSubAnimation(
                Transitions.TransitionHandler handler, TransitionInfo info,
                SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT) {
            if (mInfo != null) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        "startSubAnimation #%d.%d", mInfo.getDebugId(), info.getDebugId());
            }
            mInFlightSubAnimations++;
            if (!handler.startAnimation(
                    mTransition, info, startT, finishT, wct -> onSubAnimationFinished(info, wct))) {
                mInFlightSubAnimations--;
                return false;
            }
            return true;
        }

        private void onSubAnimationFinished(TransitionInfo info, WindowContainerTransaction wct) {
            mInFlightSubAnimations--;
            if (mInfo != null) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        "onSubAnimationFinished #%d.%d remaining=%d",
                        mInfo.getDebugId(), info.getDebugId(), mInFlightSubAnimations);
            }

            joinFinishArgs(wct);

            if (mInFlightSubAnimations == 0) {
                mFinishCB.onTransitionFinished(mFinishWCT);
            }
        }

        void joinFinishArgs(WindowContainerTransaction wct) {
            if (wct != null) {
                if (mFinishWCT == null) {
                    mFinishWCT = wct;
                } else {
                    mFinishWCT.merge(wct, true /* transfer */);
                }
            }
        }
    }

    private final ArrayList<MixedTransition> mActiveTransitions = new ArrayList<>();

    public DefaultMixedHandler(@NonNull ShellInit shellInit, @NonNull Transitions player,
            Optional<SplitScreenController> splitScreenControllerOptional,
            @Nullable PipTransitionController pipTransitionController,
            Optional<RecentsTransitionHandler> recentsHandlerOptional,
            KeyguardTransitionHandler keyguardHandler,
            Optional<DesktopTasksController> desktopTasksControllerOptional,
            Optional<UnfoldTransitionHandler> unfoldHandler,
            Optional<ActivityEmbeddingController> activityEmbeddingController) {
        mPlayer = player;
        mKeyguardHandler = keyguardHandler;
        if (Transitions.ENABLE_SHELL_TRANSITIONS
                && pipTransitionController != null
                && splitScreenControllerOptional.isPresent()) {
            // Add after dependencies because it is higher priority
            shellInit.addInitCallback(() -> {
                mPipHandler = pipTransitionController;
                pipTransitionController.setMixedHandler(this);
                mSplitHandler = splitScreenControllerOptional.get().getTransitionHandler();
                mPlayer.addHandler(this);
                if (mSplitHandler != null) {
                    mSplitHandler.setMixedHandler(this);
                }
                mRecentsHandler = recentsHandlerOptional.orElse(null);
                if (mRecentsHandler != null) {
                    mRecentsHandler.addMixer(this);
                }
                mDesktopTasksController = desktopTasksControllerOptional.orElse(null);
                mUnfoldHandler = unfoldHandler.orElse(null);
                mActivityEmbeddingController = activityEmbeddingController.orElse(null);
            }, this);
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (mSplitHandler.requestImpliesSplitToPip(request)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a PiP-enter request while "
                    + "Split-Screen is active, so treat it as Mixed.");
            if (request.getRemoteTransition() != null) {
                throw new IllegalStateException("Unexpected remote transition in"
                        + "pip-enter-from-split request");
            }
            mActiveTransitions.add(createDefaultMixedTransition(
                    MixedTransition.TYPE_ENTER_PIP_FROM_SPLIT, transition));

            WindowContainerTransaction out = new WindowContainerTransaction();
            mPipHandler.augmentRequest(transition, request, out);
            mSplitHandler.addEnterOrExitIfNeeded(request, out);
            return out;
        } else if (request.getType() == TRANSIT_PIP
                && (request.getFlags() & FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY) != 0 && (
                mActivityEmbeddingController != null)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    " Got a PiP-enter request from an Activity Embedding split");
            mActiveTransitions.add(createDefaultMixedTransition(
                    MixedTransition.TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING, transition));
            // Postpone transition splitting to later.
            WindowContainerTransaction out = new WindowContainerTransaction();
            return out;
        } else if (request.getRemoteTransition() != null
                && TransitionUtil.isOpeningType(request.getType())
                && (request.getTriggerTask() == null
                || (request.getTriggerTask().topActivityType != ACTIVITY_TYPE_HOME
                        && request.getTriggerTask().topActivityType != ACTIVITY_TYPE_RECENTS))) {
            // Only select transitions with an intent-provided remote-animation because that will
            // usually grab priority and often won't handle PiP. If there isn't an intent-provided
            // remote, then the transition will be dispatched normally and the PipHandler will
            // pick it up.
            Pair<Transitions.TransitionHandler, WindowContainerTransaction> handler =
                    mPlayer.dispatchRequest(transition, request, this);
            if (handler == null) {
                return null;
            }
            final MixedTransition mixed = createDefaultMixedTransition(
                    MixedTransition.TYPE_OPTIONS_REMOTE_AND_PIP_CHANGE, transition);
            mixed.mLeftoversHandler = handler.first;
            mActiveTransitions.add(mixed);
            if (mixed.mLeftoversHandler != mPlayer.getRemoteTransitionHandler()) {
                mixed.mHasRequestToRemote = true;
                mPlayer.getRemoteTransitionHandler().handleRequest(transition, request);
            }
            return handler.second;
        } else if (mSplitHandler.isSplitScreenVisible()
                && isOpeningType(request.getType())
                && request.getTriggerTask() != null
                && request.getTriggerTask().getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                && request.getTriggerTask().getActivityType() == ACTIVITY_TYPE_HOME) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a going-home request while "
                    + "Split-Screen is foreground, so treat it as Mixed.");
            Pair<Transitions.TransitionHandler, WindowContainerTransaction> handler =
                    mPlayer.dispatchRequest(transition, request, this);
            if (handler == null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " Lean on the remote transition handler to fetch a proper remote via"
                                + " TransitionFilter");
                handler = new Pair<>(
                        mPlayer.getRemoteTransitionHandler(),
                        new WindowContainerTransaction());
            }
            final MixedTransition mixed = createRecentsMixedTransition(
                    MixedTransition.TYPE_RECENTS_DURING_SPLIT, transition);
            mixed.mLeftoversHandler = handler.first;
            mActiveTransitions.add(mixed);
            return handler.second;
        } else if (mUnfoldHandler != null && mUnfoldHandler.shouldPlayUnfoldAnimation(request)) {
            final WindowContainerTransaction wct =
                    mUnfoldHandler.handleRequest(transition, request);
            if (wct != null) {
                mActiveTransitions.add(createDefaultMixedTransition(
                        MixedTransition.TYPE_UNFOLD, transition));
            }
            return wct;
        }
        return null;
    }

    private DefaultMixedTransition createDefaultMixedTransition(int type, IBinder transition) {
        return new DefaultMixedTransition(
                type, transition, mPlayer, this, mPipHandler, mSplitHandler, mKeyguardHandler,
                mUnfoldHandler, mActivityEmbeddingController);
    }

    @Override
    public Consumer<IBinder> handleRecentsRequest(WindowContainerTransaction outWCT) {
        if (mRecentsHandler != null) {
            if (mSplitHandler.isSplitScreenVisible()) {
                return this::setRecentsTransitionDuringSplit;
            } else if (mKeyguardHandler.isKeyguardShowing()) {
                return this::setRecentsTransitionDuringKeyguard;
            } else if (mDesktopTasksController != null
                    // Check on the default display. Recents/gesture nav is only available there
                    && mDesktopTasksController.getVisibleTaskCount(DEFAULT_DISPLAY) > 0) {
                return this::setRecentsTransitionDuringDesktop;
            }
        }
        return null;
    }

    private void setRecentsTransitionDuringSplit(IBinder transition) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a recents request while "
                + "Split-Screen is foreground, so treat it as Mixed.");
        mActiveTransitions.add(createRecentsMixedTransition(
                MixedTransition.TYPE_RECENTS_DURING_SPLIT, transition));
    }

    private void setRecentsTransitionDuringKeyguard(IBinder transition) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a recents request while "
                + "keyguard is visible, so treat it as Mixed.");
        mActiveTransitions.add(createRecentsMixedTransition(
                MixedTransition.TYPE_RECENTS_DURING_KEYGUARD, transition));
    }

    private void setRecentsTransitionDuringDesktop(IBinder transition) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Got a recents request while "
                + "desktop mode is active, so treat it as Mixed.");
        mActiveTransitions.add(createRecentsMixedTransition(
                MixedTransition.TYPE_RECENTS_DURING_DESKTOP, transition));
    }

    private MixedTransition createRecentsMixedTransition(int type, IBinder transition) {
        return new RecentsMixedTransition(type, transition, mPlayer, this, mPipHandler,
                mSplitHandler, mKeyguardHandler, mRecentsHandler, mDesktopTasksController);
    }

    static TransitionInfo subCopy(@NonNull TransitionInfo info,
            @WindowManager.TransitionType int newType, boolean withChanges) {
        final TransitionInfo out = new TransitionInfo(newType, withChanges ? info.getFlags() : 0);
        out.setTrack(info.getTrack());
        out.setDebugId(info.getDebugId());
        if (withChanges) {
            for (int i = 0; i < info.getChanges().size(); ++i) {
                out.getChanges().add(info.getChanges().get(i));
            }
        }
        for (int i = 0; i < info.getRootCount(); ++i) {
            out.addRoot(info.getRoot(i));
        }
        out.setAnimationOptions(info.getAnimationOptions());
        return out;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {

        MixedTransition mixed = null;
        for (int i = mActiveTransitions.size() - 1; i >= 0; --i) {
            if (mActiveTransitions.get(i).mTransition != transition) continue;
            mixed = mActiveTransitions.get(i);
            break;
        }

        // Offer Keyguard the opportunity to take over lock transitions - ideally we could know by
        // the time of handleRequest, but we need more information than is available at that time.
        if (KeyguardTransitionHandler.handles(info)) {
            if (mixed != null && mixed.mType != MixedTransition.TYPE_KEYGUARD) {
                final MixedTransition keyguardMixed =
                        createDefaultMixedTransition(MixedTransition.TYPE_KEYGUARD, transition);
                mActiveTransitions.add(keyguardMixed);
                Transitions.TransitionFinishCallback callback = wct -> {
                    mActiveTransitions.remove(keyguardMixed);
                    finishCallback.onTransitionFinished(wct);
                };
                final boolean hasAnimateKeyguard = animateKeyguard(
                        keyguardMixed, info, startTransaction, finishTransaction, callback,
                        mKeyguardHandler, mPipHandler);
                if (hasAnimateKeyguard) {
                    ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "Converting mixed transition into a keyguard transition");
                    // Consume the original mixed transition
                    mActiveTransitions.remove(mixed);
                    mixed.onTransitionConsumed(transition, false, null);
                    return true;
                } else {
                    // Keyguard handler cannot handle it, process through original mixed
                    mActiveTransitions.remove(keyguardMixed);
                }
            } else if (mPipHandler != null) {
                mPipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
            }
        }

        if (mixed == null) return false;

        final MixedTransition chosenTransition = mixed;
        Transitions.TransitionFinishCallback callback = wct -> {
            mActiveTransitions.remove(chosenTransition);
            finishCallback.onTransitionFinished(wct);
        };

        boolean handled = chosenTransition.startAnimation(
                transition, info, startTransaction, finishTransaction, callback);
        if (!handled) {
            mActiveTransitions.remove(chosenTransition);
        }
        return handled;
    }

    private void unlinkMissingParents(TransitionInfo from) {
        for (int i = 0; i < from.getChanges().size(); ++i) {
            final TransitionInfo.Change chg = from.getChanges().get(i);
            if (chg.getParent() == null) continue;
            if (from.getChange(chg.getParent()) == null) {
                from.getChanges().get(i).setParent(null);
            }
        }
    }

    private boolean isWithinTask(TransitionInfo info, TransitionInfo.Change chg) {
        TransitionInfo.Change curr = chg;
        while (curr != null) {
            if (curr.getTaskInfo() != null) return true;
            if (curr.getParent() == null) break;
            curr = info.getChange(curr.getParent());
        }
        return false;
    }

    /**
     * This is intended to be called by SplitCoordinator as a helper to mix a split handling
     * transition with an entering-pip change. The use-case for this is when an auto-pip change
     * gets collected into the transition which has already claimed by
     * StageCoordinator.handleRequest. This happens when launching a fullscreen app while having an
     * auto-pip activity in the foreground split pair.
     */
    // TODO(b/287704263): Remove when split/mixed are reversed.
    public boolean animatePendingEnterPipFromSplit(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            Transitions.TransitionFinishCallback finishCallback, boolean replacingPip) {
        int type = replacingPip
                ? MixedTransition.TYPE_ENTER_PIP_REPLACE_FROM_SPLIT
                : MixedTransition.TYPE_ENTER_PIP_FROM_SPLIT;
        final MixedTransition mixed = createDefaultMixedTransition(type, transition);
        mActiveTransitions.add(mixed);
        Transitions.TransitionFinishCallback callback = wct -> {
            mActiveTransitions.remove(mixed);
            finishCallback.onTransitionFinished(wct);
        };
        return mixed.startAnimation(transition, info, startT, finishT, callback);
    }

    /**
     * This is intended to be called by SplitCoordinator as a helper to mix an already-pending
     * split transition with a display-change. The use-case for this is when a display
     * change/rotation gets collected into a split-screen enter/exit transition which has already
     * been claimed by StageCoordinator.handleRequest. This happens during launcher tests.
     */
    public boolean animatePendingSplitWithDisplayChange(@NonNull IBinder transition,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo everythingElse = subCopy(info, info.getType(), true /* withChanges */);
        final TransitionInfo displayPart = subCopy(info, TRANSIT_CHANGE, false /* withChanges */);
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (isWithinTask(info, change)) continue;
            displayPart.addChange(change);
            everythingElse.getChanges().remove(i);
        }
        if (displayPart.getChanges().isEmpty()) return false;
        unlinkMissingParents(everythingElse);
        final MixedTransition mixed = createDefaultMixedTransition(
                MixedTransition.TYPE_DISPLAY_AND_SPLIT_CHANGE, transition);
        mActiveTransitions.add(mixed);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animation is a mix of display change "
                + "and split change.");
        // We need to split the transition into 2 parts: the split part and the display part.
        mixed.mInFlightSubAnimations = 2;

        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mixed.mInFlightSubAnimations;
            mixed.joinFinishArgs(wct);
            if (mixed.mInFlightSubAnimations > 0) return;
            mActiveTransitions.remove(mixed);
            finishCallback.onTransitionFinished(mixed.mFinishWCT);
        };

        // Dispatch the display change. This will most-likely be taken by the default handler.
        // Do this first since the first handler used will apply the startT; the display change
        // needs to take a screenshot before that happens so we need it to be the first handler.
        mixed.mLeftoversHandler = mPlayer.dispatchTransition(mixed.mTransition, displayPart,
                startT, finishT, finishCB, mSplitHandler);

        // Note: at this point, startT has probably already been applied, so we are basically
        // giving splitHandler an empty startT. This is currently OK because display-change will
        // grab a screenshot and paste it on top anyways.
        mSplitHandler.startPendingAnimation(transition, everythingElse, startT, finishT, finishCB);
        return true;
    }

    /**
     * For example: pip is entering in rotation 0, and then the display changes to rotation 90
     * before the pip transition is ready. So the info contains both the entering pip and display
     * change. In this case, the pip can go to the end state in new rotation directly, and let the
     * display level animation cover all changed participates.
     */
    public void animateEnteringPipWithDisplayChange(@NonNull IBinder transition,
            @NonNull TransitionInfo info, @NonNull TransitionInfo.Change pipChange,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // In order to play display level animation, force the type to CHANGE (it could be PIP).
        final TransitionInfo changeInfo = info.getType() != TRANSIT_CHANGE
                ? subCopy(info, TRANSIT_CHANGE, true /* withChanges */) : info;
        final MixedTransition mixed = createDefaultMixedTransition(
                MixedTransition.TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE, transition);
        mActiveTransitions.add(mixed);
        mixed.mInFlightSubAnimations = 2;
        final Transitions.TransitionFinishCallback finishCB = wct -> {
            --mixed.mInFlightSubAnimations;
            mixed.joinFinishArgs(wct);
            if (mixed.mInFlightSubAnimations > 0) return;
            mActiveTransitions.remove(mixed);
            finishCallback.onTransitionFinished(mixed.mFinishWCT);
        };
        // Perform the display animation first.
        mixed.mLeftoversHandler = mPlayer.dispatchTransition(mixed.mTransition, changeInfo,
                startT, finishT, finishCB, mPipHandler);
        // Use a standalone finish transaction for pip because it will apply immediately.
        final SurfaceControl.Transaction pipFinishT = new SurfaceControl.Transaction();
        mPipHandler.startEnterAnimation(pipChange, startT, pipFinishT, wct -> {
            // Apply immediately to avoid potential flickering by bounds change at the end of
            // display animation.
            mPipHandler.applyTransaction(wct);
            finishCB.onTransitionFinished(null /* wct */);
        });
        // Jump to the pip end state directly and make sure the real finishT have the latest state.
        mPipHandler.end();
        mPipHandler.syncPipSurfaceState(info, startT, finishT);
    }

    private static boolean animateKeyguard(@NonNull final MixedTransition mixed,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull KeyguardTransitionHandler keyguardHandler,
            PipTransitionController pipHandler) {
        if (mixed.mFinishT == null) {
            mixed.mFinishT = finishTransaction;
            mixed.mFinishCB = finishCallback;
        }
        // Sync pip state.
        if (pipHandler != null) {
            pipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
        }
        return mixed.startSubAnimation(keyguardHandler, info, startTransaction, finishTransaction);
    }

    /** Use to when split use intent to enter, check if this enter transition should be mixed or
     * not.*/
    public boolean isIntentInPip(PendingIntent intent) {
        // Check if this intent package is same as pip one or not, if true we want let the pip
        // task enter split.
        if (mPipHandler != null) {
            return mPipHandler
                    .isPackageActiveInPip(SplitScreenUtils.getPackageName(intent.getIntent()));
        }
        return false;
    }

    /** Use to when split use taskId to enter, check if this enter transition should be mixed or
     * not.*/
    public boolean isTaskInPip(int taskId, ShellTaskOrganizer shellTaskOrganizer) {
        // Check if this intent package is same as pip one or not, if true we want let the pip
        // task enter split.
        if (mPipHandler != null) {
            return mPipHandler.isPackageActiveInPip(
                    SplitScreenUtils.getPackageName(taskId, shellTaskOrganizer));
        }
        return false;
    }

    /** @return whether the transition-request represents a pip-entry. */
    public boolean requestHasPipEnter(TransitionRequestInfo request) {
        return mPipHandler.requestHasPipEnter(request);
    }

    /** Whether a particular change is a window that is entering pip. */
    // TODO(b/287704263): Remove when split/mixed are reversed.
    public boolean isEnteringPip(TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        return mPipHandler.isEnteringPip(change, transitType);
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        for (int i = 0; i < mActiveTransitions.size(); ++i) {
            if (mActiveTransitions.get(i).mTransition != mergeTarget) continue;

            MixedTransition mixed = mActiveTransitions.get(i);
            if (mixed.mInFlightSubAnimations <= 0) {
                // Already done, so no need to end it.
                return;
            }
            mixed.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
        }
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        MixedTransition mixed = null;
        for (int i = mActiveTransitions.size() - 1; i >= 0; --i) {
            if (mActiveTransitions.get(i).mTransition != transition) continue;
            mixed = mActiveTransitions.remove(i);
            break;
        }
        if (mixed != null) {
            mixed.onTransitionConsumed(transition, aborted, finishT);
        }
    }

    /**
     * Update an incoming {@link TransitionInfo} with the leashes from an existing
     * {@link TransitionInfo} so that it can take over some parts of the animation without
     * reparenting to new transition roots.
     */
    static void handoverTransitionLeashes(
            @NonNull TransitionInfo from,
            @NonNull TransitionInfo to,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT) {

        // Show the roots in case they contain new changes not present in the original transition.
        for (int j = to.getRootCount() - 1; j >= 0; --j) {
            startT.show(to.getRoot(j).getLeash());
        }

        // Find all of the leashes from the original transition.
        Map<WindowContainerToken, TransitionInfo.Change> originalChanges = new ArrayMap<>();
        for (TransitionInfo.Change oldChange : from.getChanges()) {
            if (oldChange.getContainer() != null) {
                originalChanges.put(oldChange.getContainer(), oldChange);
            }
        }

        // Merge the animation leashes by re-using the original ones if we see the same container
        // in the new transition and the old.
        for (TransitionInfo.Change newChange : to.getChanges()) {
            if (originalChanges.containsKey(newChange.getContainer())) {
                final TransitionInfo.Change oldChange = originalChanges.get(
                        newChange.getContainer());
                startT.reparent(newChange.getLeash(), null);
                newChange.setLeash(oldChange.getLeash());
            }
        }
    }
}
